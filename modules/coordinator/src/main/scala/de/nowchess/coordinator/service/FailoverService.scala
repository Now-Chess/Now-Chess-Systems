package de.nowchess.coordinator.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import io.quarkus.redis.datasource.RedisDataSource
import scala.jdk.CollectionConverters.*
import scala.compiletime.uninitialized
import org.jboss.logging.Logger
import de.nowchess.coordinator.dto.InstanceMetadata
import de.nowchess.coordinator.grpc.CoreGrpcClient

@ApplicationScoped
class FailoverService:
  // scalafix:off DisableSyntax.var
  @Inject
  private var redis: RedisDataSource = uninitialized

  @Inject
  private var instanceRegistry: InstanceRegistry = uninitialized

  @Inject
  private var coreGrpcClient: CoreGrpcClient = uninitialized

  private val log         = Logger.getLogger(classOf[FailoverService])
  private var redisPrefix = "nowchess"
  // scalafix:on DisableSyntax.var

  def setRedisPrefix(prefix: String): Unit =
    redisPrefix = prefix

  def onInstanceStreamDropped(instanceId: String): Unit =
    log.infof("Instance %s stream dropped, triggering failover", instanceId)

    val startTime = System.currentTimeMillis()
    instanceRegistry.markInstanceDead(instanceId)

    val gameIds = getOrphanedGames(instanceId)
    log.infof("Found %d orphaned games for instance %s", gameIds.size, instanceId)

    if gameIds.nonEmpty then
      val healthyInstances = instanceRegistry.getAllInstances
        .filter(_.state == "HEALTHY")
        .sortBy(_.subscriptionCount)

      if healthyInstances.nonEmpty then
        distributeGames(gameIds, healthyInstances, instanceId)

        val elapsed = System.currentTimeMillis() - startTime
        log.infof("Failover completed in %dms for instance %s", elapsed, instanceId)
      else log.warnf("No healthy instances available for failover of %s", instanceId)

    cleanupDeadInstance(instanceId)

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
            log.infof("Migrated %d games from %s to %s", subscribed, deadId, target.instanceId)
            true
          else false
        catch
          case ex: Exception =>
            log.warnf(ex, "Failed to migrate batch to %s, trying next", target.instanceId)
            false
      if success then true else tryMigrateBatch(batch, batchIdx, instances, deadId, attempt + 1)

  private def cleanupDeadInstance(instanceId: String): Unit =
    val setKey = s"$redisPrefix:instance:$instanceId:games"
    redis.key(classOf[String]).del(setKey)
    log.infof("Cleaned up games set for instance %s", instanceId)
