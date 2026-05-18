package de.nowchess.coordinator.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import io.quarkus.redis.datasource.RedisDataSource
import scala.jdk.CollectionConverters.*
import scala.compiletime.uninitialized
import org.jboss.logging.Logger
import de.nowchess.coordinator.dto.InstanceMetadata
import de.nowchess.coordinator.grpc.CoreGrpcClient
import de.nowchess.coordinator.config.CoordinatorConfig
import io.fabric8.kubernetes.client.KubernetesClient
import io.smallrye.mutiny.Uni
import java.time.Duration

@ApplicationScoped
class FailoverService:
  // scalafix:off DisableSyntax.var
  @Inject
  private var redis: RedisDataSource = uninitialized

  @Inject
  private var instanceRegistry: InstanceRegistry = uninitialized

  @Inject
  private var coreGrpcClient: CoreGrpcClient = uninitialized

  @Inject
  private var config: CoordinatorConfig = uninitialized

  @Inject
  private var kubeClientInstance: Instance[KubernetesClient] = uninitialized

  private val log         = Logger.getLogger(classOf[FailoverService])
  private var redisPrefix = "nowchess"
  // scalafix:on DisableSyntax.var

  private def kubeClientOpt: Option[KubernetesClient] =
    if kubeClientInstance.isUnsatisfied then None
    else Some(kubeClientInstance.get())

  def setRedisPrefix(prefix: String): Unit =
    redisPrefix = prefix

  def onInstanceStreamDropped(instanceId: String): Uni[Unit] =
    log.infof("Instance %s stream dropped, triggering failover", instanceId)

    val startTime = System.currentTimeMillis()
    instanceRegistry.markInstanceDead(instanceId)
    deleteK8sPod(instanceId)

    val gameIds = getOrphanedGames(instanceId)
    log.infof("Found %d orphaned games for instance %s", gameIds.size, instanceId)

    if gameIds.isEmpty then
      cleanupDeadInstance(instanceId)
      Uni.createFrom().item(())
    else
      waitForHealthyInstanceAsync()
        .onItem()
        .transform { _ =>
          val healthyInstances = instanceRegistry.getAllInstances
            .filter(_.state == "HEALTHY")
            .sortBy(_.subscriptionCount)
          distributeGames(gameIds, healthyInstances, instanceId)

          val elapsed = System.currentTimeMillis() - startTime
          log.infof("Failover completed in %dms for instance %s", elapsed, instanceId)
          cleanupDeadInstance(instanceId)
          ()
        }
        .onFailure()
        .recoverWithItem { _ =>
          log.errorf(
            "No healthy instance appeared within %s — games orphaned for %s",
            config.failoverWaitTimeout,
            instanceId,
          )
          ()
        }

  private def getOrphanedGames(instanceId: String): List[String] =
    val setKey = s"$redisPrefix:instance:$instanceId:games"
    redis.set(classOf[String]).smembers(setKey).asScala.toList

  private def distributeGames(
      gameIds: List[String],
      healthyInstances: List[InstanceMetadata],
      deadInstanceId: String,
  ): Unit =
    if gameIds.nonEmpty && healthyInstances.nonEmpty then
      val batchSize = math.max(1, gameIds.size / healthyInstances.size)
      val batches   = gameIds.grouped(batchSize).toList

      batches.zipWithIndex.foreach { case (batch, idx) =>
        if !tryMigrateBatch(batch, idx, healthyInstances, deadInstanceId) then
          log.errorf(
            "Failed to migrate batch of %d games from %s to any healthy instance",
            batch.size,
            deadInstanceId,
          )
      }

  @scala.annotation.tailrec
  private def tryMigrateBatch(
      batch: List[String],
      batchIdx: Int,
      instances: List[InstanceMetadata],
      deadId: String,
      attempt: Int = 0,
  ): Boolean =
    if attempt >= instances.size then false
    else
      val target = instances((batchIdx + attempt) % instances.size)
      val success =
        try
          val subscribed = coreGrpcClient.batchResubscribeGames(target.hostname, target.grpcPort, batch)
          if subscribed > 0 then
            updateGameInstanceMappings(batch, deadId, target.instanceId)
            log.infof("Migrated %d games from %s to %s", subscribed, deadId, target.instanceId)
            true
          else false
        catch
          case ex: Exception =>
            log.errorf(ex, "Failed to migrate batch to %s, trying next", target.instanceId)
            false
      if success then true else tryMigrateBatch(batch, batchIdx, instances, deadId, attempt + 1)

  private def updateGameInstanceMappings(gameIds: List[String], deadId: String, targetId: String): Unit =
    try
      val fromKey = s"$redisPrefix:instance:$deadId:games"
      val toKey   = s"$redisPrefix:instance:$targetId:games"
      gameIds.foreach { gameId =>
        redis.set(classOf[String]).sadd(toKey, gameId)
        redis.value(classOf[String]).set(s"$redisPrefix:game:$gameId:instance", targetId)
      }
    catch
      case ex: Exception =>
        log.errorf(ex, "Failed to update game instance mappings")

  private def deleteK8sPod(instanceId: String): Unit =
    kubeClientOpt match
      case None =>
        log.debugf("Kubernetes client not available, skipping pod deletion for %s", instanceId)
      case Some(kube) =>
        try
          val pods = kube
            .pods()
            .inNamespace(config.k8sNamespace)
            .withLabel(config.k8sRolloutLabelSelector)
            .list()
            .getItems
            .asScala

          pods.find(pod => instanceId.contains(pod.getMetadata.getName)) match
            case Some(pod) =>
              val podName = pod.getMetadata.getName
              kube.pods().inNamespace(config.k8sNamespace).withName(podName).withGracePeriod(0L).delete()
              log.infof("Force-deleted pod %s for dead instance %s", podName, instanceId)
            case None =>
              log.debugf("No pod found for instance %s, skipping deletion", instanceId)
        catch
          case ex: Exception =>
            log.errorf(ex, "Failed to delete pod for instance %s", instanceId)

  private def cleanupDeadInstance(instanceId: String): Unit =
    val setKey = s"$redisPrefix:instance:$instanceId:games"
    redis.key(classOf[String]).del(setKey)
    log.infof("Cleaned up games set for instance %s", instanceId)

  private def waitForHealthyInstanceAsync(): Uni[InstanceMetadata] =
    Uni
      .createFrom()
      .deferred(() =>
        instanceRegistry.getAllInstances
          .filter(_.state == "HEALTHY")
          .sortBy(_.subscriptionCount)
          .headOption match
          case Some(inst) => Uni.createFrom().item(inst)
          case None       => Uni.createFrom().failure(new RuntimeException("no healthy instance")),
      )
      .onFailure()
      .retry()
      .withBackOff(Duration.ofMillis(500))
      .expireIn(config.failoverWaitTimeout.toMillis)
