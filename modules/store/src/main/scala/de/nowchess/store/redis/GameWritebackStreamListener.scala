package de.nowchess.store.redis

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.dto.GameWritebackEventDto
import de.nowchess.store.config.RedisConfig
import de.nowchess.store.service.GameWritebackService
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.stream.{StreamMessage, XGroupCreateArgs, XReadGroupArgs}
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.context.ManagedExecutor
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}
import java.util.UUID

@Startup
@ApplicationScoped
class GameWritebackStreamListener:
  @Inject
  // scalafix:off DisableSyntax.var
  var redis: RedisDataSource                         = uninitialized
  @Inject var objectMapper: ObjectMapper             = uninitialized
  @Inject var writebackService: GameWritebackService = uninitialized
  @Inject var executor: ManagedExecutor              = uninitialized
  @Inject var redisConfig: RedisConfig               = uninitialized
  // scalafix:on

  private val log       = Logger.getLogger(classOf[GameWritebackStreamListener])
  private val groupName = "store-writeback"

  private def streamKey = s"${redisConfig.prefix}:game-writeback"
  private def dlqKey    = s"${redisConfig.prefix}:game-writeback-dlq"
  private val maxRetries = 3
  private val consumerId = UUID.randomUUID().toString

  @PostConstruct
  def startListening(): Unit =
    createGroupIfAbsent()
    executor.submit(new Runnable:
      def run(): Unit = pollLoop()
    )
    log.infof("Started listening to game-writeback stream (consumer=%s)", consumerId)

  private def createGroupIfAbsent(): Unit =
    Try(redis.stream(classOf[String]).xgroupCreate(streamKey, groupName, "0", new XGroupCreateArgs().mkstream())) match
      case Failure(ex) if Option(ex.getMessage).exists(_.contains("BUSYGROUP")) => ()
      case Failure(ex) => log.warnf(ex, "Failed to create consumer group")
      case Success(_)  => ()

  private def pollLoop(): Unit =
    while true do
      Try {
        val messages = redis.stream(classOf[String]).xreadgroup(
          groupName,
          consumerId,
          streamKey,
          ">",
          new XReadGroupArgs().count(10).block(java.time.Duration.ofSeconds(2)),
        )
        Option(messages).foreach(_.forEach(msg => handleMessage(msg)))
      } match
        case Failure(ex) => log.warnf(ex, "Error in writeback poll loop")
        case Success(_)  => ()

  private def handleMessage(msg: StreamMessage[String, String, String]): Unit =
    val payload = msg.payload()
    val json    = payload.get("data")
    val attempt = Option(payload.get("attempt")).flatMap(_.toIntOption).getOrElse(0)

    Try(objectMapper.readValue(json, classOf[GameWritebackEventDto])) match
      case Failure(ex) =>
        log.errorf(ex, "Unparseable writeback event, sending to DLQ: %s", json)
        xadd(dlqKey, json, attempt)
        ack(msg.id())
      case Success(event) =>
        Try(writebackService.writeBack(event)) match
          case Success(_) =>
            ack(msg.id())
          case Failure(ex) if attempt + 1 < maxRetries =>
            log.warnf(ex, "Writeback failed for gameId=%s attempt=%d, retrying", event.gameId, attempt)
            xadd(streamKey, json, attempt + 1)
            ack(msg.id())
          case Failure(ex) =>
            log.errorf(ex, "Writeback failed for gameId=%s after %d attempts, sending to DLQ", event.gameId, maxRetries)
            xadd(dlqKey, json, attempt)
            ack(msg.id())

  private def ack(id: String): Unit =
    Try(redis.stream(classOf[String]).xack(streamKey, groupName, id)) match
      case Failure(ex) => log.warnf(ex, "Failed to ack message %s", id)
      case Success(_)  => ()

  private def xadd(key: String, json: String, attempt: Int): Unit =
    Try(redis.stream(classOf[String]).xadd(key, Map("data" -> json, "attempt" -> attempt.toString).asJava)) match
      case Failure(ex) => log.errorf(ex, "Failed to publish to stream %s", key)
      case Success(_)  => ()
