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

import java.util.concurrent.atomic.AtomicReference
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

  private val log           = Logger.getLogger(classOf[AutoScaler])
  private val lastScaleTime = new java.util.concurrent.atomic.AtomicLong(0L)
  private val avgLoadRef    = new AtomicReference[Double](0.0)

  private def kubeClientOpt: Option[KubernetesClient] =
    if kubeClientInstance.isUnsatisfied then None
    else Some(kubeClientInstance.get())

  private val argoApiVersion = "argoproj.io/v1alpha1"
  private val argoKind       = "Rollout"

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

  def checkAndScale: Unit =
    if config.autoScaleEnabled then
      val now  = System.currentTimeMillis()
      val last = lastScaleTime.get()
      if now - last >= 120000 && lastScaleTime.compareAndSet(last, now) then
        val instances = instanceRegistry.getAllInstances.filter(_.state == "HEALTHY")
        if instances.nonEmpty then
          val avgLoad = instances.map(_.subscriptionCount).sum.toDouble / instances.size
          avgLoadRef.set(avgLoad)

          if avgLoad > config.scaleUpThreshold * config.maxGamesPerCore then scaleUp()
          else if avgLoad < config.scaleDownThreshold * config.maxGamesPerCore && instances.size > config.scaleMinReplicas then scaleDown()

  def scaleUp(): Unit =
    log.info("Scaling up Argo Rollout")
    kubeClientOpt match
      case None =>
        log.warn("Kubernetes client not available, cannot scale")
      case Some(kube) =>
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
                case replicas: Integer =>
                  val currentReplicas = replicas.intValue()
                  val maxReplicas     = config.scaleMaxReplicas

                  if currentReplicas < maxReplicas then
                    spec.put("replicas", Integer.valueOf(currentReplicas + 1))
                    kube
                      .genericKubernetesResources(argoApiVersion, argoKind)
                      .inNamespace(config.k8sNamespace)
                      .resource(rollout)
                      .update()
                    meterRegistry.counter("nowchess.coordinator.scale.events", "direction", "up").increment()
                    log.infof(
                      "Scaled up %s from %d to %d replicas",
                      config.k8sRolloutName,
                      currentReplicas,
                      currentReplicas + 1,
                    )
                    loadBalancer.rebalance
                  else log.infof("Already at max replicas %d for %s", maxReplicas, config.k8sRolloutName)
                case _ => ()
            }
          }
        catch
          case ex: Exception =>
            meterRegistry.counter("nowchess.coordinator.scale.failures", "direction", "up").increment()
            log.warnf(ex, "Failed to scale up %s", config.k8sRolloutName)

  def scaleDown(): Unit =
    log.info("Scaling down Argo Rollout")
    val underloadedInstance = instanceRegistry.getAllInstances
      .filter(_.state == "HEALTHY")
      .minByOption(_.subscriptionCount)

    underloadedInstance.foreach { inst =>
      log.infof("Marking instance %s for drain before scale-down", inst.instanceId)
      failoverService
        .onInstanceStreamDropped(inst.instanceId)
        .subscribe()
        .`with`(
          _ => log.debugf("Instance %s drained for scale-down", inst.instanceId),
          ex => log.warnf(ex, "Drain failed for %s, proceeding with scale-down", inst.instanceId),
        )
    }

    kubeClientOpt match
      case None =>
        log.warn("Kubernetes client not available, cannot scale")
      case Some(kube) =>
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
                case replicas: Integer =>
                  val currentReplicas = replicas.intValue()
                  val minReplicas     = config.scaleMinReplicas

                  if currentReplicas > minReplicas then
                    spec.put("replicas", Integer.valueOf(currentReplicas - 1))
                    kube
                      .genericKubernetesResources(argoApiVersion, argoKind)
                      .inNamespace(config.k8sNamespace)
                      .resource(rollout)
                      .update()
                    meterRegistry.counter("nowchess.coordinator.scale.events", "direction", "down").increment()
                    log.infof(
                      "Scaled down %s from %d to %d replicas",
                      config.k8sRolloutName,
                      currentReplicas,
                      currentReplicas - 1,
                    )
                  else log.infof("Already at min replicas %d for %s", minReplicas, config.k8sRolloutName)
                case _ => ()
            }
          }
        catch
          case ex: Exception =>
            meterRegistry.counter("nowchess.coordinator.scale.failures", "direction", "down").increment()
            log.warnf(ex, "Failed to scale down %s", config.k8sRolloutName)
