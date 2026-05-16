package de.nowchess.coordinator.service

import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import de.nowchess.coordinator.config.CoordinatorConfig
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.client.KubernetesClient
import io.micrometer.core.instrument.{Gauge, MeterRegistry}
import io.quarkus.scheduler.Scheduled
import org.jboss.logging.Logger
import io.fabric8.kubernetes.client.KubernetesClientException
import scala.jdk.CollectionConverters.*

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import scala.compiletime.uninitialized

@ApplicationScoped
class AutoScaler:
  // scalafix:off DisableSyntax.var
  @Inject
  private var kubeClientInstance: Instance[KubernetesClient] = uninitialized

  @Inject
  private var config: CoordinatorConfig = uninitialized

  @Inject
  private var instanceRegistry: InstanceRegistry = uninitialized

  @Inject
  private var loadBalancer: LoadBalancer = uninitialized

  @Inject
  private var failoverService: FailoverService = uninitialized

  @Inject
  private var meterRegistry: MeterRegistry = uninitialized
  // scalafix:on DisableSyntax.var

  private val log                  = Logger.getLogger(classOf[AutoScaler])
  private val lastScaleTime        = new java.util.concurrent.atomic.AtomicLong(0L)
  private val avgLoadRef           = new AtomicReference[Double](0.0)
  private val drainingForScaleDown = ConcurrentHashMap.newKeySet[String]()

  def isDrainingForScaleDown(instanceId: String): Boolean =
    drainingForScaleDown.contains(instanceId)

  def clearDraining(instanceId: String): Unit =
    drainingForScaleDown.remove(instanceId)

  def clearDrainingByPodName(podName: String): Unit =
    drainingForScaleDown.asScala.find(id => id.contains(podName)).foreach(drainingForScaleDown.remove)

  private def kubeClientOpt: Option[KubernetesClient] =
    if kubeClientInstance.isUnsatisfied then None
    else Some(kubeClientInstance.get())

  private val argoApiVersion    = "argoproj.io/v1alpha1"
  private val argoKind          = "Rollout"
  private val metricsApiVersion = "metrics.k8s.io/v1beta1"

  @PostConstruct
  def initMetrics(): Unit =
    Gauge
      .builder("nowchess.coordinator.load.average", avgLoadRef, _.get())
      .register(meterRegistry)
    meterRegistry.counter("nowchess.coordinator.scale.events", "direction", "up").increment(0)
    meterRegistry.counter("nowchess.coordinator.scale.events", "direction", "down").increment(0)
    meterRegistry.counter("nowchess.coordinator.scale.failures", "direction", "up").increment(0)
    meterRegistry.counter("nowchess.coordinator.scale.failures", "direction", "down").increment(0)
    ()

  @Scheduled(every = "10s")
  def periodicScaleCheck(): Unit =
    try checkAndScale
    catch case ex: Exception => log.warnf(ex, "Auto-scale check failed")

  // scalafix:off DisableSyntax.asInstanceOf
  private def rolloutSpec(rollout: GenericKubernetesResource): Option[java.util.Map[String, AnyRef]] =
    Option(rollout.get[AnyRef]("spec")).collect { case m: java.util.Map[?, ?] =>
      m.asInstanceOf[java.util.Map[String, AnyRef]]
    }
  // scalafix:on DisableSyntax.asInstanceOf

  private def parseMillicores(s: String): Long =
    if s.endsWith("n") then s.dropRight(1).toLongOption.map(_ / 1000000).getOrElse(0L)
    else if s.endsWith("m") then s.dropRight(1).toLongOption.getOrElse(0L)
    else s.toLongOption.map(_ * 1000).getOrElse(0L)

  private def parseBytes(s: String): Long =
    if s.endsWith("Ki") then s.dropRight(2).toLongOption.map(_ * 1024L).getOrElse(0L)
    else if s.endsWith("Mi") then s.dropRight(2).toLongOption.map(_ * 1024L * 1024L).getOrElse(0L)
    else if s.endsWith("Gi") then s.dropRight(2).toLongOption.map(_ * 1024L * 1024L * 1024L).getOrElse(0L)
    else if s.endsWith("K") then s.dropRight(1).toLongOption.map(_ * 1000L).getOrElse(0L)
    else if s.endsWith("M") then s.dropRight(1).toLongOption.map(_ * 1000L * 1000L).getOrElse(0L)
    else if s.endsWith("G") then s.dropRight(1).toLongOption.map(_ * 1000L * 1000L * 1000L).getOrElse(0L)
    else s.toLongOption.getOrElse(0L)

  private def exceedsRatio(
      used: Long,
      request: Long,
      threshold: Double,
      resource: String,
      instanceId: String,
  ): Boolean =
    if request <= 0 then false
    else
      val ratio = used.toDouble / request.toDouble
      log.debugf(
        "Instance %s %s: %d used / %d requested = %.0f%%",
        instanceId,
        resource,
        used,
        request,
        ratio * 100,
      )
      ratio > threshold

  // scalafix:off DisableSyntax.asInstanceOf
  private def isResourceConstrained(instanceId: String): Boolean =
    kubeClientOpt.fold(false) { kube =>
      try
        val pods =
          kube.pods().inNamespace(config.k8sNamespace).withLabel(config.k8sRolloutLabelSelector).list().getItems.asScala
        pods.find(pod => instanceId.contains(pod.getMetadata.getName)).exists { pod =>
          try
            val requests = Option(pod.getSpec)
              .flatMap(s => Option(s.getContainers))
              .flatMap(cs => if cs.isEmpty then None else Option(cs.get(0)))
              .flatMap(c => Option(c.getResources))
              .flatMap(r => Option(r.getRequests))

            val cpuRequestMillis =
              requests.flatMap(m => Option(m.get("cpu"))).map(q => parseMillicores(q.toString)).getOrElse(0L)
            val memRequestBytes =
              requests.flatMap(m => Option(m.get("memory"))).map(q => parseBytes(q.toString)).getOrElse(0L)

            if cpuRequestMillis <= 0 && memRequestBytes <= 0 then
              log.debugf("No resource requests found for instance %s, skipping resource check", instanceId)
              false
            else
              val metricsRes = kube
                .genericKubernetesResources(metricsApiVersion, "PodMetrics")
                .inNamespace(config.k8sNamespace)
                .withName(pod.getMetadata.getName)
                .get()
              val metricsMap = metricsRes.asInstanceOf[java.util.Map[String, AnyRef]]
              val usageOpt = Option(metricsMap.get("metrics"))
                .map(_.asInstanceOf[java.util.Map[String, AnyRef]])
                .flatMap(m => Option(m.get("containers")).map(_.asInstanceOf[java.util.List[AnyRef]]))
                .filter(!_.isEmpty)
                .map(_.get(0).asInstanceOf[java.util.Map[String, AnyRef]])
                .flatMap(c => Option(c.get("usage")).map(_.asInstanceOf[java.util.Map[String, AnyRef]]))

              usageOpt.exists { usage =>
                val cpuUsed = Option(usage.get("cpu")).map(v => parseMillicores(v.toString)).getOrElse(0L)
                val memUsed = Option(usage.get("memory")).map(v => parseBytes(v.toString)).getOrElse(0L)
                exceedsRatio(cpuUsed, cpuRequestMillis, config.scaleCpuThresholdPercent, "CPU", instanceId) ||
                exceedsRatio(memUsed, memRequestBytes, config.scaleMemoryThresholdPercent, "memory", instanceId)
              }
          catch case _: Exception => false
        }
      catch
        case ex: Exception =>
          log.debugf(ex, "Failed to check resource metrics for %s", instanceId)
          false
    }
  // scalafix:on DisableSyntax.asInstanceOf

  def checkAndScale: Unit =
    if config.autoScaleEnabled then
      val now  = System.currentTimeMillis()
      val last = lastScaleTime.get()
      if now - last >= 120000 && lastScaleTime.compareAndSet(last, now) then
        val instances = instanceRegistry.getAllInstances.filter(_.state == "HEALTHY")
        if instances.nonEmpty then
          val avgLoad       = instances.map(_.subscriptionCount).sum.toDouble / instances.size
          val scaleUpLoad   = config.scaleUpThreshold * config.maxGamesPerCore
          val scaleDownLoad = config.scaleDownThreshold * config.maxGamesPerCore
          avgLoadRef.set(avgLoad)

          val constrainedInstance = instances.find(inst => isResourceConstrained(inst.instanceId))
          val hasHighCpuOrMemory  = constrainedInstance.isDefined

          log.infof(
            "Scale check: instances=%d avgLoad=%.1f scaleUpAt=%.1f scaleDownAt=%.1f resourceConstrained=%s",
            instances.size,
            avgLoad,
            scaleUpLoad,
            scaleDownLoad,
            constrainedInstance.map(_.instanceId).getOrElse("none"),
          )

          if avgLoad > scaleUpLoad || hasHighCpuOrMemory then scaleUp()
          else if avgLoad < scaleDownLoad && instances.size > config.scaleMinReplicas
          then scaleDown()

  private def patchRolloutReplicas(
      kube: KubernetesClient,
      direction: String,
      delta: Int,
      canScale: Int => Boolean,
      atLimit: Int => Unit,
      onSuccess: (Int, Int) => Unit,
      maxRetries: Int = 3,
  ): Unit =
    def attempt(retries: Int): Unit =
      try
        Option(
          kube
            .genericKubernetesResources(argoApiVersion, argoKind)
            .inNamespace(config.k8sNamespace)
            .withName(config.k8sRolloutName)
            .get(),
        ).foreach { rollout =>
          rolloutSpec(rollout).foreach { spec =>
            spec.get("replicas") match
              case current: Integer =>
                val n = current.intValue()
                if !canScale(n) then atLimit(n)
                else
                  spec.put("replicas", Integer.valueOf(n + delta))
                  kube
                    .genericKubernetesResources(argoApiVersion, argoKind)
                    .inNamespace(config.k8sNamespace)
                    .resource(rollout)
                    .update()
                  meterRegistry.counter("nowchess.coordinator.scale.events", "direction", direction).increment()
                  onSuccess(n, n + delta)
              case _ => ()
          }
        }
      catch
        case ex: KubernetesClientException if ex.getCode == 409 =>
          if retries > 0 then
            log.debugf("Conflict scaling %s %s, retrying (%d left)", direction, config.k8sRolloutName, retries - 1)
            attempt(retries - 1)
          else
            meterRegistry.counter("nowchess.coordinator.scale.failures", "direction", direction).increment()
            log.errorf(ex, "Failed to scale %s %s after conflict retries", direction, config.k8sRolloutName)
        case ex: Exception =>
          meterRegistry.counter("nowchess.coordinator.scale.failures", "direction", direction).increment()
          log.errorf(ex, "Failed to scale %s %s", direction, config.k8sRolloutName)
    attempt(maxRetries)

  def scaleUp(): Unit =
    log.info("Scaling up Argo Rollout")
    kubeClientOpt match
      case None => log.warn("Kubernetes client not available, cannot scale")
      case Some(kube) =>
        patchRolloutReplicas(
          kube,
          direction = "up",
          delta = 1,
          canScale = _ < config.scaleMaxReplicas,
          atLimit = n => log.infof("Already at max replicas %d for %s", n, config.k8sRolloutName),
          onSuccess = (from, to) =>
            log.infof("Scaled up %s from %d to %d replicas", config.k8sRolloutName, from, to)
            loadBalancer.rebalance,
        )

  def scaleDown(): Unit =
    log.info("Scaling down Argo Rollout")
    val underloadedInstance = instanceRegistry.getAllInstances
      .filter(_.state == "HEALTHY")
      .minByOption(_.subscriptionCount)

    underloadedInstance.foreach { inst =>
      log.infof("Marking instance %s for drain before scale-down", inst.instanceId)
      drainingForScaleDown.add(inst.instanceId)
      failoverService
        .onInstanceStreamDropped(inst.instanceId)
        .subscribe()
        .`with`(
          _ => log.debugf("Instance %s drained for scale-down", inst.instanceId),
          ex => log.warnf(ex, "Drain failed for %s, proceeding with scale-down", inst.instanceId),
        )
    }

    kubeClientOpt match
      case None => log.warn("Kubernetes client not available, cannot scale")
      case Some(kube) =>
        patchRolloutReplicas(
          kube,
          direction = "down",
          delta = -1,
          canScale = _ > config.scaleMinReplicas,
          atLimit = n => log.infof("Already at min replicas %d for %s", n, config.k8sRolloutName),
          onSuccess = (from, to) =>
            log.infof("Scaled down %s from %d to %d replicas", config.k8sRolloutName, from, to)
            underloadedInstance.foreach(inst => forceDeletePod(inst.instanceId, kube)),
        )

  private def forceDeletePod(instanceId: String, kube: KubernetesClient): Unit =
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
          kube.pods().inNamespace(config.k8sNamespace).withName(pod.getMetadata.getName).withGracePeriod(0L).delete()
          log.infof("Force-deleted pod for drained instance %s", instanceId)
        case None =>
          log.debugf("No pod found for drained instance %s, skipping deletion", instanceId)
    catch
      case ex: Exception =>
        log.warnf(ex, "Failed to force-delete pod for drained instance %s", instanceId)
