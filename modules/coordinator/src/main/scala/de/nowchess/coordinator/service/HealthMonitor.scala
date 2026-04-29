package de.nowchess.coordinator.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import de.nowchess.coordinator.config.CoordinatorConfig
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.api.model.Pod
import io.quarkus.redis.datasource.RedisDataSource
import scala.jdk.CollectionConverters.*
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import java.time.Instant

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

  private val log         = Logger.getLogger(classOf[HealthMonitor])
  private var redisPrefix = "nowchess"
  // scalafix:on DisableSyntax.var

  private def kubeClientOpt: Option[KubernetesClient] =
    if kubeClientInstance.isUnsatisfied then None
    else Some(kubeClientInstance.get())

  def setRedisPrefix(prefix: String): Unit =
    redisPrefix = prefix

  def checkInstanceHealth: Unit =
    val instances = instanceRegistry.getAllInstances
    instances.foreach { inst =>
      val isHealthy = checkHealth(inst.instanceId)
      if !isHealthy && inst.state == "HEALTHY" then
        log.warnf("Instance %s marked unhealthy", inst.instanceId)
        instanceRegistry.markInstanceDead(inst.instanceId)
    }

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
          podName.contains(instanceId) && isPodReady(pod)
        }
      catch
        case ex: Exception =>
          log.debugf(ex, "K8s pod status check failed for %s", instanceId)
          true
    }

  def watchK8sPods: Unit =
    kubeClientOpt match
      case None =>
        log.debug("Kubernetes client not available for pod watch")
      case Some(kube) =>
        try
          val pods = kube
            .pods()
            .inNamespace(config.k8sNamespace)
            .withLabel(config.k8sRolloutLabelSelector)
            .list()
            .getItems
            .asScala

          val instances = instanceRegistry.getAllInstances
          instances.foreach { inst =>
            val matchingPod = pods.find { pod =>
              pod.getMetadata.getName.contains(inst.instanceId)
            }

            matchingPod match
              case Some(pod) =>
                val isReady = isPodReady(pod)
                if !isReady && inst.state == "HEALTHY" then
                  log.warnf("Pod %s not ready, marking instance %s dead", pod.getMetadata.getName, inst.instanceId)
                  instanceRegistry.markInstanceDead(inst.instanceId)
              case None =>
                if inst.state == "HEALTHY" then
                  log.warnf("No pod found for instance %s, marking dead", inst.instanceId)
                  instanceRegistry.markInstanceDead(inst.instanceId)
          }
        catch
          case ex: Exception =>
            log.warnf(ex, "Failed to watch k8s pods")

  private def isPodReady(pod: Pod): Boolean =
    Option(pod.getStatus)
      .flatMap(s => Option(s.getConditions))
      .exists(_.asScala.exists(cond => cond.getType == "Ready" && cond.getStatus == "True"))
