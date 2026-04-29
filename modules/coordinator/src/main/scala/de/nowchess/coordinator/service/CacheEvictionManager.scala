package de.nowchess.coordinator.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import io.quarkus.redis.datasource.RedisDataSource
import de.nowchess.coordinator.config.CoordinatorConfig
import com.fasterxml.jackson.databind.ObjectMapper
import scala.jdk.CollectionConverters.*
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.util.Try
import java.time.Instant
import de.nowchess.coordinator.grpc.CoreGrpcClient

@ApplicationScoped
class CacheEvictionManager:
  // scalafix:off DisableSyntax.var
  @Inject
  private var redis: RedisDataSource = uninitialized

  @Inject
  private var config: CoordinatorConfig = uninitialized

  @Inject
  private var instanceRegistry: InstanceRegistry = uninitialized

  @Inject
  private var coreGrpcClient: CoreGrpcClient = uninitialized

  @Inject
  private var objectMapper: ObjectMapper = uninitialized

  private val log         = Logger.getLogger(classOf[CacheEvictionManager])
  private var redisPrefix = "nowchess"
  // scalafix:on DisableSyntax.var

  def setRedisPrefix(prefix: String): Unit =
    redisPrefix = prefix

  def evictStaleGames: Unit =
    log.info("Starting cache eviction scan")

    val pattern         = s"$redisPrefix:game:entry:*"
    val keys            = redis.key(classOf[String]).keys(pattern)
    val now             = System.currentTimeMillis()
    val idleThresholdMs = config.gameIdleThreshold.toMillis

    val evictedCount = keys.asScala.foldLeft(0) { (count, key) =>
      try
        Option(redis.value(classOf[String]).get(key)).fold(count) { value =>
          val gameId      = key.stripPrefix(s"$redisPrefix:game:entry:")
          val lastUpdated = extractLastUpdatedTimestamp(value)

          if lastUpdated > 0 && (now - lastUpdated) > idleThresholdMs then
            findInstanceWithGame(gameId).fold(count) { instance =>
              try
                coreGrpcClient.evictGames(instance.hostname, instance.grpcPort, List(gameId))
                redis.key(classOf[String]).del(key)
                log.infof("Evicted idle game %s from %s", gameId, instance.instanceId)
                count + 1
              catch
                case ex: Exception =>
                  log.warnf(ex, "Failed to evict game %s", gameId)
                  count
            }
          else count
        }
      catch
        case ex: Exception =>
          log.warnf(ex, "Error processing game key %s", key)
          count
    }

    log.infof("Cache eviction scan completed, evicted %d games", evictedCount)

  private def extractLastUpdatedTimestamp(json: String): Long =
    Try {
      val parsed = objectMapper.readTree(json)
      Option(parsed.get("lastHeartbeat"))
        .filter(_.isTextual)
        .fold(0L)(lh => Instant.parse(lh.asText()).toEpochMilli)
    }.getOrElse(0L)

  private def findInstanceWithGame(gameId: String): Option[de.nowchess.coordinator.dto.InstanceMetadata] =
    try
      instanceRegistry.getAllInstances.find { instance =>
        val setKey = s"$redisPrefix:instance:${instance.instanceId}:games"
        redis.set(classOf[String]).sismember(setKey, gameId)
      }
    catch
      case ex: Exception =>
        log.debugf(ex, "Failed to find instance for game %s", gameId)
        None
