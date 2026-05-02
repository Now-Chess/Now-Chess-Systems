package de.nowchess.coordinator.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import io.quarkus.redis.datasource.ReactiveRedisDataSource
import scala.jdk.CollectionConverters.*
import scala.compiletime.uninitialized
import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.coordinator.dto.InstanceMetadata
import java.util.concurrent.ConcurrentHashMap
import io.smallrye.mutiny.Uni
import org.jboss.logging.Logger

@ApplicationScoped
class InstanceRegistry:
  // scalafix:off DisableSyntax.var
  @Inject
  private var redis: ReactiveRedisDataSource = uninitialized
  private var redisPrefix                    = "nowchess"
  // scalafix:on DisableSyntax.var

  private val log       = Logger.getLogger(classOf[InstanceRegistry])
  private val mapper    = ObjectMapper()
  private val instances = ConcurrentHashMap[String, InstanceMetadata]()

  def setRedisPrefix(prefix: String): Unit =
    redisPrefix = prefix

  def getInstance(instanceId: String): Option[InstanceMetadata] =
    Option(instances.get(instanceId))

  def getAllInstances: List[InstanceMetadata] =
    instances.values.asScala.toList

  def updateInstanceFromRedis(instanceId: String): Uni[Unit] =
    val key = s"$redisPrefix:instances:$instanceId"
    redis
      .value(classOf[String])
      .get(key)
      .onItem()
      .transformToUni { value =>
        try
          val metadata = mapper.readValue(value, classOf[InstanceMetadata])
          val isNew    = !instances.containsKey(instanceId)
          instances.put(instanceId, metadata)
          if isNew then
            log.infof("Instance %s joined registry (subscriptions=%d)", instanceId, metadata.subscriptionCount)
          else
            log.debugf(
              "Instance %s updated (subscriptions=%d state=%s)",
              instanceId,
              metadata.subscriptionCount,
              metadata.state,
            )
          Uni.createFrom().item(())
        catch
          case ex: Exception =>
            log.warnf(ex, "Failed to parse instance metadata for %s", instanceId)
            Uni.createFrom().item(())
      }
      .onFailure()
      .recoverWithItem(())

  def markInstanceDead(instanceId: String): Unit =
    instances.computeIfPresent(instanceId, (_, inst) => inst.copy(state = "DEAD"))
    log.infof("Instance %s marked dead", instanceId)

  def removeInstance(instanceId: String): Unit =
    instances.remove(instanceId)
    log.infof("Instance %s removed from registry", instanceId)
