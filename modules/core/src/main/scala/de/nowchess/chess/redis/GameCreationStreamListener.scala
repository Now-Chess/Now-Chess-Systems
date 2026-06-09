package de.nowchess.chess.redis

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.dto.{GameCreationRequestDto, GameCreationResponseDto}
import de.nowchess.api.event.{EventEnvelope, EventType}
import de.nowchess.chess.config.RedisConfig
import de.nowchess.chess.service.GameCreationService
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.stream.{StreamMessage, XAddArgs, XGroupCreateArgs, XReadGroupArgs}
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.context.ManagedExecutor
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}
import java.time.Duration
import java.util.UUID

@ApplicationScoped
class GameCreationStreamListener:

  // scalafix:off DisableSyntax.var
  @Inject var redis: RedisDataSource               = uninitialized
  @Inject var objectMapper: ObjectMapper           = uninitialized
  @Inject var creationService: GameCreationService = uninitialized
  @Inject var executor: ManagedExecutor            = uninitialized
  @Inject var redisConfig: RedisConfig             = uninitialized
  @ConfigProperty(name = "nowchess.game-creation-stream.enabled", defaultValue = "true")
  private var streamEnabled: Boolean = true
  // scalafix:on DisableSyntax.var

  private val log          = Logger.getLogger(classOf[GameCreationStreamListener])
  private val groupName    = "core-game-creation"
  private val consumerId   = UUID.randomUUID().toString
  private val maxRetries   = 3
  private val maxStreamLen = 1000L

  private def requestStream: String  = s"${redisConfig.prefix}:game-creation"
  private def responseStream: String = s"${redisConfig.prefix}:game-creation-response"
  private def dlqStream: String      = s"${redisConfig.prefix}:dlq"

  def start(@Observes _ev: StartupEvent): Unit =
    if streamEnabled then
      createGroupIfAbsent()
      executor.submit(
        new Runnable:
          def run(): Unit = pollLoop(),
      )
      log.infof("Game-creation request listener started (consumer=%s)", consumerId)

  private def createGroupIfAbsent(): Unit =
    Try(
      redis
        .stream(classOf[String])
        .xgroupCreate(requestStream, groupName, "0", new XGroupCreateArgs().mkstream()),
    ) match
      case Failure(ex) if Option(ex.getMessage).exists(_.contains("BUSYGROUP")) => ()
      case Failure(ex) => log.warnf(ex, "Failed to create game-creation consumer group")
      case Success(_)  => ()

  private def pollLoop(): Unit =
    while true do
      Try {
        val messages = redis
          .stream(classOf[String])
          .xreadgroup(
            groupName,
            consumerId,
            requestStream,
            ">",
            new XReadGroupArgs().count(10).block(Duration.ofSeconds(2)),
          )
        Option(messages).foreach(_.forEach(handleMessage))
      } match
        case Failure(ex) => log.warnf(ex, "Error in game-creation poll loop")
        case Success(_)  => ()

  private def handleMessage(msg: StreamMessage[String, String, String]): Unit =
    val json    = msg.payload().get("data")
    val attempt = Option(msg.payload().get("attempt")).flatMap(_.toIntOption).getOrElse(0)
    Try(objectMapper.readValue(json, classOf[EventEnvelope])) match
      case Failure(ex) =>
        log.errorf(ex, "Unparseable game-creation event, sending to DLQ: %s", json)
        toDlq(EventType.GameCreationRequest.toString, json, ex, attempt)
        ack(msg.id())
      case Success(envelope) =>
        processEnvelope(msg, envelope, json, attempt)

  private def processEnvelope(
      msg: StreamMessage[String, String, String],
      envelope: EventEnvelope,
      json: String,
      attempt: Int,
  ): Unit =
    Try {
      val req   = objectMapper.treeToValue(envelope.payload, classOf[GameCreationRequestDto])
      val entry = creationService.createGame(req)
      publishResponse(envelope.correlationId, GameCreationResponseDto(Some(entry.gameId)))
    } match
      case Success(_) => ack(msg.id())
      case Failure(ex) if attempt + 1 < maxRetries =>
        log.warnf(ex, "Game creation failed (attempt %d), retrying", attempt)
        retry(json, attempt + 1)
        ack(msg.id())
      case Failure(ex) =>
        log.errorf(ex, "Game creation failed after %d attempts, sending to DLQ", maxRetries)
        publishResponse(envelope.correlationId, GameCreationResponseDto(None, Some("Game creation failed")))
        toDlq(envelope.`type`.toString, json, ex, attempt)
        ack(msg.id())

  private def publishResponse(correlationId: Option[String], resp: GameCreationResponseDto): Unit =
    val payload  = objectMapper.valueToTree[com.fasterxml.jackson.databind.JsonNode](resp)
    val envelope = EventEnvelope.of(EventType.GameCreationResponse, payload, correlationId)
    xadd(responseStream, Map("data" -> objectMapper.writeValueAsString(envelope)))

  private def retry(json: String, attempt: Int): Unit =
    xadd(requestStream, Map("data" -> json, "attempt" -> attempt.toString))

  private def toDlq(eventType: String, json: String, error: Throwable, attempt: Int): Unit =
    xadd(
      dlqStream,
      Map(
        "data"      -> json,
        "eventType" -> eventType,
        "error"     -> Option(error.getMessage).getOrElse(error.getClass.getName),
        "attempt"   -> (attempt + 1).toString,
      ),
    )

  private def ack(id: String): Unit =
    Try(redis.stream(classOf[String]).xack(requestStream, groupName, id)) match
      case Failure(ex) => log.warnf(ex, "Failed to ack message %s", id)
      case Success(_)  => ()

  private def xadd(key: String, fields: Map[String, String]): Unit =
    Try(
      redis
        .stream(classOf[String])
        .xadd(key, new XAddArgs().maxlen(maxStreamLen).nearlyExactTrimming(), fields.asJava),
    ) match
      case Failure(ex) => log.errorf(ex, "Failed to publish to stream %s", key)
      case Success(_)  => ()
