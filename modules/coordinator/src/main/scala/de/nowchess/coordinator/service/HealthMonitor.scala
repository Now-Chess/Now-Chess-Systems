package de.nowchess.coordinator.service

import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import io.quarkus.scheduler.Scheduled
import de.nowchess.coordinator.config.CoordinatorConfig
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.WatcherException
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.runtime.StartupEvent
import scala.jdk.CollectionConverters.*
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import java.time.Instant
import de.nowchess.coordinator.grpc.CoordinatorGrpcServer
import de.nowchess.coordinator.dto.InstanceMetadata

@ApplicationScoped
class HealthMonitor:
  // scalafix:off DisableSyntax.var
  @Inject
  private var kubeClientInstance: Instance[KubernetesClient] = uninitialized

  @Inject
  private var config: CoordinatorConfig = uninitialized

  @Inject
  private var instanceRegistry: InstanceRegistry = uninitialized

  @Inject
  private var redis: RedisDataSource = uninitialized

  @Inject
  private var meterRegistry: MeterRegistry = uninitialized

  @Inject
  private var grpcServerInstance: Instance[CoordinatorGrpcServer] = uninitialized

  @Inject
  private var failoverService: FailoverService = uninitialized

  @Inject
  private var autoScaler: AutoScaler = uninitialized

  private val log         = Logger.getLogger(classOf[HealthMonitor])
  private var redisPrefix = "nowchess"
  // scalafix:on DisableSyntax.var

  private def kubeClientOpt: Option[KubernetesClient] =
    if kubeClientInstance.isUnsatisfied then None
    else Some(kubeClientInstance.get())

  private def grpcServerOpt: Option[CoordinatorGrpcServer] =
    if grpcServerInstance.isUnsatisfied then None
    else Some(grpcServerInstance.get())

  def setRedisPrefix(prefix: String): Unit =
    redisPrefix = prefix

  @PostConstruct
  def initializeMetrics(): Unit =
    meterRegistry.counter("nowchess.coordinator.health.checks").increment(0)
    meterRegistry.counter("nowchess.coordinator.pods.unhealthy").increment(0)

  def onStartup(@Observes ev: StartupEvent): Unit =
    instanceRegistry.loadAllFromRedis()
    val loaded = instanceRegistry.getAllInstances
    log.infof("Startup: loaded %d instances from Redis", loaded.size)
    if loaded.nonEmpty then
      val timeoutMs = config.startupValidationTimeout.toMillis
      Thread.ofVirtual().start(() => validateStartupInstances(timeoutMs))
    startPodWatch()

  @Scheduled(every = "10s")
  def periodicHealthCheck(): Unit =
    try checkInstanceHealth()
    catch case ex: Exception => log.warnf(ex, "Health check failed")

  def checkInstanceHealth(): Unit =
    meterRegistry.counter("nowchess.coordinator.health.checks").increment()
    val evicted = instanceRegistry.evictStaleInstances(config.instanceDeadTimeout)
    if evicted.nonEmpty then
      log.warnf("Evicted %d stale instances: %s", evicted.size, evicted.mkString(", "))
      evicted.foreach(deleteK8sPod)
      val unexpectedEvictions = evicted.filterNot(autoScaler.isDrainingForScaleDown)
      evicted.foreach(autoScaler.clearDraining)
      if unexpectedEvictions.nonEmpty then autoScaler.scaleUp()
    val instances = instanceRegistry.getAllInstances
    val failed = instances.collect { inst =>
      val isHealthy = checkHealth(inst.instanceId)
      if !isHealthy && inst.state == "HEALTHY" then
        log.warnf("Instance %s marked unhealthy", inst.instanceId)
        instanceRegistry.markInstanceDead(inst.instanceId)
        deleteK8sPod(inst.instanceId)
        Some(inst.instanceId)
      else None
    }.flatten
    val unexpectedFailures = failed.filterNot(autoScaler.isDrainingForScaleDown)
    if unexpectedFailures.nonEmpty then autoScaler.scaleUp()

  private def checkHealth(instanceId: String): Boolean =
    val redisHealthy = checkRedisHeartbeat(instanceId)
    val k8sHealthy   = checkK8sPodStatus(instanceId)
    redisHealthy && k8sHealthy

  private def checkRedisHeartbeat(instanceId: String): Boolean =
    try
      val key = s"$redisPrefix:instances:$instanceId"
      redis.key(classOf[String]).pttl(key) > 0
    catch
      case ex: Exception =>
        log.debugf(ex, "Redis heartbeat check failed for %s", instanceId)
        false

  private def checkK8sPodStatus(instanceId: String): Boolean =
    kubeClientOpt.fold(true) { kube =>
      try
        val pods = kube
          .pods()
          .inNamespace(config.k8sNamespace)
          .withLabel(config.k8sRolloutLabelSelector)
          .list()
          .getItems
          .asScala

        pods.exists { pod =>
          val podName = pod.getMetadata.getName
          podName.endsWith(instanceId) && isPodReady(pod)
        }
      catch
        case ex: Exception =>
          log.debugf(ex, "K8s pod status check failed for %s", instanceId)
          true
    }

  private def startPodWatch(): Unit =
    kubeClientOpt match
      case None => log.debug("K8s client unavailable, skipping pod watch")
      case Some(kube) =>
        try
          kube
            .pods()
            .inNamespace(config.k8sNamespace)
            .withLabel(config.k8sRolloutLabelSelector)
            .watch(new Watcher[Pod]:
              override def eventReceived(action: Watcher.Action, pod: Pod): Unit =
                action match
                  case Watcher.Action.DELETED =>
                    handlePodGone(pod)
                  case Watcher.Action.MODIFIED if Option(pod.getMetadata.getDeletionTimestamp).isDefined =>
                    handlePodTerminating(pod)
                  case _ => ()

              override def onClose(cause: WatcherException): Unit =
                Option(cause).foreach { ex =>
                  log.warnf(ex, "Pod watch closed, restarting")
                  startPodWatch()
                },
            )
          log.info("Pod watch started")
        catch case ex: Exception => log.warnf(ex, "Failed to start pod watch")

  private def isPodReady(pod: Pod): Boolean =
    Option(pod.getStatus)
      .flatMap(s => Option(s.getConditions))
      .exists(_.asScala.exists(cond => cond.getType == "Ready" && cond.getStatus == "True"))

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

          pods.find(pod => pod.getMetadata.getName.endsWith(instanceId)) match
            case Some(pod) =>
              val podName = pod.getMetadata.getName
              kube.pods().inNamespace(config.k8sNamespace).withName(podName).withGracePeriod(0L).delete()
              meterRegistry.counter("nowchess.coordinator.pods.deleted").increment()
              log.infof("Force-deleted pod %s for dead instance %s", podName, instanceId)
            case None =>
              log.debugf("No pod found for instance %s, skipping deletion", instanceId)
        catch
          case ex: Exception =>
            log.errorf(
              ex,
              "Failed to delete pod for instance %s — removing from registry to prevent blocking scale-down",
              instanceId,
            )
            instanceRegistry.removeInstance(instanceId)

  private def validateStartupInstances(timeoutMs: Long): Unit =
    Thread.sleep(timeoutMs)
    grpcServerOpt.foreach { grpcServer =>
      instanceRegistry.getAllInstances.foreach { inst =>
        if !grpcServer.hasActiveStream(inst.instanceId) then
          log.warnf(
            "Startup: instance %s did not reconnect within %dms — evicting",
            inst.instanceId,
            timeoutMs,
          )
          instanceRegistry.removeInstance(inst.instanceId)
          deleteK8sPod(inst.instanceId)
      }
    }

  private def handlePodTerminating(pod: Pod): Unit =
    findRegisteredInstance(pod).foreach { inst =>
      if inst.state == "HEALTHY" then
        meterRegistry.counter("nowchess.coordinator.pods.unhealthy").increment()
        log.warnf(
          "Pod %s terminating — marking instance %s dead",
          pod.getMetadata.getName,
          inst.instanceId,
        )
        instanceRegistry.markInstanceDead(inst.instanceId)
    }

  private def handlePodGone(pod: Pod): Unit =
    val podName = pod.getMetadata.getName
    findRegisteredInstance(pod).foreach { inst =>
      log.warnf("Pod %s deleted — triggering failover for %s", podName, inst.instanceId)
      failoverService
        .onInstanceStreamDropped(inst.instanceId)
        .subscribe()
        .`with`(
          _ => (),
          ex => log.warnf(ex, "Failover for %s failed", inst.instanceId),
        )
    }

  private def findRegisteredInstance(pod: Pod): Option[InstanceMetadata] =
    val podName = pod.getMetadata.getName
    instanceRegistry.getAllInstances.find(inst => podName.endsWith(inst.instanceId))
