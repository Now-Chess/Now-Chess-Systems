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

@ApplicationScoped
class InstanceRegistry:
  // scalafix:off DisableSyntax.var
  @Inject
  private var redis: ReactiveRedisDataSource = uninitialized
  private var redisPrefix                     = "nowchess"
  // scalafix:on DisableSyntax.var

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
    redis.value(classOf[String])
      .get(key)
      .onItem().transformToUni { value =>
        try
          val metadata = mapper.readValue(value, classOf[InstanceMetadata])
          instances.put(instanceId, metadata)
          Uni.createFrom().item(())
        catch case _: Exception => Uni.createFrom().item(())
      }
      .onFailure().recoverWithItem(())

  def markInstanceDead(instanceId: String): Unit =
    instances.computeIfPresent(instanceId, (_, inst) => inst.copy(state = "DEAD"))
    ()

  def removeInstance(instanceId: String): Unit =
    instances.remove(instanceId)
    ()
