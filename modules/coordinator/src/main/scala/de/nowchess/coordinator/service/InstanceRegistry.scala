package de.nowchess.coordinator.service

import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import io.quarkus.redis.datasource.ReactiveRedisDataSource
import io.quarkus.redis.datasource.RedisDataSource
import scala.jdk.CollectionConverters.*
import scala.compiletime.uninitialized
import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.coordinator.dto.InstanceMetadata
import de.nowchess.coordinator.config.CoordinatorConfig
import java.util.concurrent.ConcurrentHashMap
import java.time.{Duration, Instant}
import io.micrometer.core.instrument.{Gauge, MeterRegistry}
import io.smallrye.mutiny.Uni
import org.jboss.logging.Logger

@ApplicationScoped
class InstanceRegistry:
  // scalafix:off DisableSyntax.var
  @Inject
  private var redis: ReactiveRedisDataSource = uninitialized

  @Inject
  private var syncRedis: RedisDataSource = uninitialized
  private var redisPrefix                = "nowchess"

  @Inject
  private var meterRegistry: MeterRegistry = uninitialized

  @Inject
  private var config: CoordinatorConfig = uninitialized
  // scalafix:on DisableSyntax.var

  private val log       = Logger.getLogger(classOf[InstanceRegistry])
  private val mapper    = ObjectMapper()
  private val instances = ConcurrentHashMap[String, InstanceMetadata]()

  @PostConstruct
  def initMetrics(): Unit =
    Gauge
      .builder("nowchess.coordinator.instances.active", instances, m => m.size().toDouble)
      .register(meterRegistry)
    meterRegistry.counter("nowchess.coordinator.instances.joined").increment(0)
    meterRegistry.counter("nowchess.coordinator.instances.removed").increment(0)
    meterRegistry.counter("nowchess.coordinator.instances.evicted").increment(0)
    ()

  def setRedisPrefix(prefix: String): Unit =
    redisPrefix = prefix

  def loadAllFromRedis(): Unit =
    val keys = syncRedis.key(classOf[String]).keys(s"$redisPrefix:instances:*")
    keys.asScala.foreach { key =>
      val instanceId = key.stripPrefix(s"$redisPrefix:instances:")
      val json       = syncRedis.value(classOf[String]).get(key)
      Option(json).foreach { jsonStr =>
        try
          val metadata = mapper.readValue(jsonStr, classOf[InstanceMetadata])
          instances.put(instanceId, metadata)
          log.infof("Startup: loaded instance %s from Redis", instanceId)
        catch
          case ex: Exception =>
            log.warnf(ex, "Startup: failed to parse instance %s", instanceId)
      }
    }

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
          Option(value).fold(
            {
              log.debugf("Instance %s metadata missing from Redis (may have expired)", instanceId)
              Uni.createFrom().item(())
            },
          ) { json =>
            val metadata = mapper.readValue(json, classOf[InstanceMetadata])
            val isNew    = !instances.containsKey(instanceId)
            instances.put(instanceId, metadata)
            if isNew then
              meterRegistry.counter("nowchess.coordinator.instances.joined").increment()
              log.infof("Instance %s joined registry (subscriptions=%d)", instanceId, metadata.subscriptionCount)
            else
              log.debugf(
                "Instance %s updated (subscriptions=%d state=%s)",
                instanceId,
                metadata.subscriptionCount,
                metadata.state,
              )
            val ttlMs = config.heartbeatTtl.toMillis
            redis
              .key(classOf[String])
              .pexpire(key, ttlMs)
              .map(_ => ())
              .onFailure()
              .recoverWithItem(())
          }
        catch
          case ex: Exception =>
            log.errorf(ex, "Failed to parse instance metadata for %s — removing from registry", instanceId)
            instances.remove(instanceId)
            meterRegistry.counter("nowchess.coordinator.instances.removed").increment()
            Uni.createFrom().item(())
      }
      .onFailure()
      .recoverWithItem(())

  def markInstanceDead(instanceId: String): Unit =
    instances.computeIfPresent(instanceId, (_, inst) => inst.copy(state = "DEAD"))
    log.infof("Instance %s marked dead", instanceId)

  def removeInstance(instanceId: String): Unit =
    instances.remove(instanceId)
    meterRegistry.counter("nowchess.coordinator.instances.removed").increment()
    log.infof("Instance %s removed from registry", instanceId)

  def evictStaleInstances(maxAge: Duration): List[String] =
    val cutoff = Instant.now().minus(maxAge)
    val stale = instances.asScala
      .collect { case (id, inst) =>
        try
          val isHeartbeatStale = Instant.parse(inst.lastHeartbeat).isBefore(cutoff)
          val isDead           = inst.state == "DEAD"
          if isHeartbeatStale || isDead then Some(id) else None
        catch case _: Exception => None
      }
      .flatten
      .toList
    stale.foreach { id =>
      val inst = Option(instances.remove(id))
      meterRegistry.counter("nowchess.coordinator.instances.evicted").increment()
      inst.foreach { i =>
        if i.state == "DEAD" then log.warnf("Evicted dead instance %s", id)
        else log.warnf("Evicted stale instance %s (heartbeat older than %s)", id, maxAge)
      }
    }
    stale
