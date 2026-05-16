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
    drainingForScaleDown.asScala.find(podName.contains).foreach(drainingForScaleDown.remove)

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

  // scalafix:off DisableSyntax.asInstanceOf
  private def isResourceConstrained(instanceId: String): Boolean =
    kubeClientOpt.fold(false) { kube =>
      try
        val pods =
          kube.pods().inNamespace(config.k8sNamespace).withLabel(config.k8sRolloutLabelSelector).list().getItems.asScala
        pods.find(_.getMetadata.getName.contains(instanceId)).exists { pod =>
          try
            val metricsRes = kube
              .genericKubernetesResources(metricsApiVersion, "PodMetrics")
              .inNamespace(config.k8sNamespace)
              .withName(pod.getMetadata.getName)
              .get()
            val metricsMap = metricsRes.asInstanceOf[java.util.Map[String, AnyRef]]
            Option(metricsMap.get("metrics"))
              .map(_.asInstanceOf[java.util.Map[String, AnyRef]])
              .flatMap(m => Option(m.get("containers")).map(_.asInstanceOf[java.util.List[AnyRef]]))
              .filter(!_.isEmpty)
              .map(_.get(0).asInstanceOf[java.util.Map[String, AnyRef]])
              .flatMap(c => Option(c.get("usage")).map(_.asInstanceOf[java.util.Map[String, AnyRef]]))
              .flatMap(u => Option(u.get("cpu")))
              .map(_.toString)
              .exists { cpuStr =>
                val cpuMillis =
                  if cpuStr.endsWith("m") then cpuStr.dropRight(1).toLongOption.getOrElse(0L)
                  else cpuStr.toLongOption.map(_ * 1000).getOrElse(0L)
                cpuMillis > 800
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
          val avgLoad = instances.map(_.subscriptionCount).sum.toDouble / instances.size
          avgLoadRef.set(avgLoad)

          val hasHighCpuOrMemory = instances.exists(inst => isResourceConstrained(inst.instanceId))

          if avgLoad > config.scaleUpThreshold * config.maxGamesPerCore || hasHighCpuOrMemory then scaleUp()
          else if avgLoad < config.scaleDownThreshold * config.maxGamesPerCore && instances.size > config.scaleMinReplicas
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
      pods.find(_.getMetadata.getName.contains(instanceId)) match
        case Some(pod) =>
          kube.pods().inNamespace(config.k8sNamespace).withName(pod.getMetadata.getName).withGracePeriod(0L).delete()
          log.infof("Force-deleted pod for drained instance %s", instanceId)
        case None =>
          log.debugf("No pod found for drained instance %s, skipping deletion", instanceId)
    catch
      case ex: Exception =>
        log.warnf(ex, "Failed to force-delete pod for drained instance %s", instanceId)
