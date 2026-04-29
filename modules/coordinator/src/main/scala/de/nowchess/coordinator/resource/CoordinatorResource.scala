package de.nowchess.coordinator.resource

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import de.nowchess.coordinator.service.{AutoScaler, FailoverService, InstanceRegistry, LoadBalancer}
import de.nowchess.coordinator.dto.InstanceMetadata
import org.jboss.logging.Logger

@Path("/api/coordinator")
@ApplicationScoped
class CoordinatorResource:
  // scalafix:off DisableSyntax.var
  @Inject
  private var instanceRegistry: InstanceRegistry = uninitialized

  @Inject
  private var loadBalancer: LoadBalancer = uninitialized

  @Inject
  private var autoScaler: AutoScaler = uninitialized

  @Inject
  private var failoverService: FailoverService = uninitialized
  // scalafix:on DisableSyntax.var

  private val log = Logger.getLogger(classOf[CoordinatorResource])

  @GET
  @Path("/instances")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def listInstances: java.util.List[InstanceMetadata] =
    instanceRegistry.getAllInstances.asJava

  @GET
  @Path("/metrics")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def getMetrics: MetricsDto =
    val instances  = instanceRegistry.getAllInstances
    val loads      = instances.map(_.subscriptionCount)
    val totalGames = loads.sum
    val avgLoad    = if instances.nonEmpty then loads.sum.toDouble / instances.size else 0.0
    val maxLoad    = if loads.nonEmpty then loads.max else 0
    val minLoad    = if loads.nonEmpty then loads.min else 0

    MetricsDto(
      totalInstances = instances.size,
      healthyInstances = instances.count(_.state == "HEALTHY"),
      deadInstances = instances.count(_.state == "DEAD"),
      totalGames = totalGames,
      avgGamesPerCore = avgLoad,
      maxGamesPerCore = maxLoad,
      minGamesPerCore = minLoad,
      instances = instances,
    )

  @POST
  @Path("/rebalance")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def triggerRebalance: scala.collection.Map[String, String] =
    log.info("Manual rebalance triggered")
    loadBalancer.rebalance
    Map("status" -> "rebalance_started")

  @POST
  @Path("/failover/{instanceId}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def triggerFailover(@PathParam("instanceId") instanceId: String): scala.collection.Map[String, String] =
    log.infof("Manual failover triggered for instance %s", instanceId)
    failoverService.onInstanceStreamDropped(instanceId)
    Map("status" -> "failover_started", "instanceId" -> instanceId)

  @POST
  @Path("/scale-up")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def triggerScaleUp: scala.collection.Map[String, String] =
    log.info("Manual scale up triggered")
    autoScaler.scaleUp()
    Map("status" -> "scale_up_started")

  @POST
  @Path("/scale-down")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def triggerScaleDown: scala.collection.Map[String, String] =
    log.info("Manual scale down triggered")
    autoScaler.scaleDown()
    Map("status" -> "scale_down_started")

case class MetricsDto(
    totalInstances: Int,
    healthyInstances: Int,
    deadInstances: Int,
    totalGames: Int,
    avgGamesPerCore: Double,
    maxGamesPerCore: Int,
    minGamesPerCore: Int,
    instances: List[InstanceMetadata],
)
