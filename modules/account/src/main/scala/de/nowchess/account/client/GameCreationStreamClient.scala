package de.nowchess.account.client

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.account.config.RedisConfig
import de.nowchess.api.dto.{GameCreationRequestDto, GameCreationResponseDto, PlayerInfoDto, TimeControlDto}
import de.nowchess.api.game.GameMode
import de.nowchess.api.player.PlayerType
import de.nowchess.api.event.{EventEnvelope, EventType}
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
import java.util.concurrent.{CompletableFuture, ConcurrentHashMap, TimeUnit}

@ApplicationScoped
class GameCreationStreamClient:

  // scalafix:off DisableSyntax.var
  @Inject var redis: RedisDataSource     = uninitialized
  @Inject var redisConfig: RedisConfig   = uninitialized
  @Inject var objectMapper: ObjectMapper = uninitialized
  @Inject var executor: ManagedExecutor  = uninitialized
  @ConfigProperty(name = "nowchess.game-creation-stream.enabled", defaultValue = "true")
  private var streamEnabled: Boolean = true
  // scalafix:on DisableSyntax.var

  private val log          = Logger.getLogger(classOf[GameCreationStreamClient])
  private val instanceId   = UUID.randomUUID().toString
  private val groupName    = s"account-game-creation-$instanceId"
  private val consumerId   = instanceId
  private val maxStreamLen = 1000L
  private val timeout      = Duration.ofSeconds(10)

  private val pending = new ConcurrentHashMap[String, CompletableFuture[GameCreationResponseDto]]()

  private def requestStream: String  = s"${redisConfig.prefix}:game-creation"
  private def responseStream: String = s"${redisConfig.prefix}:game-creation-response"

  def start(@Observes _ev: StartupEvent): Unit =
    if streamEnabled then
      createGroupIfAbsent()
      executor.submit(
        new Runnable:
          def run(): Unit = pollLoop(),
      )
      log.infof("Game-creation response listener started (consumer=%s)", consumerId)

  def createGame(req: CoreCreateGameRequest): GameCreationResponseDto =
    val correlationId = UUID.randomUUID().toString
    val future        = new CompletableFuture[GameCreationResponseDto]()
    pending.put(correlationId, future)
    Try {
      val payload  = objectMapper.valueToTree[com.fasterxml.jackson.databind.JsonNode](toDto(req))
      val envelope = EventEnvelope.of(EventType.GameCreationRequest, payload, Some(correlationId))
      publish(requestStream, envelope)
      future.get(timeout.toMillis, TimeUnit.MILLISECONDS)
    } match
      case Success(resp) =>
        pending.remove(correlationId)
        resp
      case Failure(ex) =>
        pending.remove(correlationId)
        log.errorf(ex, "Game creation request %s failed", correlationId)
        GameCreationResponseDto(None, Some("Game creation request timed out or failed"))

  private def toDto(req: CoreCreateGameRequest): GameCreationRequestDto =
    GameCreationRequestDto(
      white = req.white.map(p => PlayerInfoDto(p.id, p.displayName, PlayerType.Human)),
      black = req.black.map(p => PlayerInfoDto(p.id, p.displayName, PlayerType.Human)),
      timeControl = req.timeControl.map(t => TimeControlDto(t.limitSeconds, t.incrementSeconds, t.daysPerMove)),
      mode = req.mode.map(_ => GameMode.Authenticated),
    )

  private def createGroupIfAbsent(): Unit =
    Try(
      redis
        .stream(classOf[String])
        .xgroupCreate(responseStream, groupName, "0", new XGroupCreateArgs().mkstream()),
    ) match
      case Failure(ex) if Option(ex.getMessage).exists(_.contains("BUSYGROUP")) => ()
      case Failure(ex) => log.warnf(ex, "Failed to create response consumer group")
      case Success(_)  => ()

  private def pollLoop(): Unit =
    while true do
      Try {
        val messages = redis
          .stream(classOf[String])
          .xreadgroup(
            groupName,
            consumerId,
            responseStream,
            ">",
            new XReadGroupArgs().count(10).block(Duration.ofSeconds(2)),
          )
        Option(messages).foreach(_.forEach(handleResponse))
      } match
        case Failure(ex) => log.warnf(ex, "Error in game-creation response poll loop")
        case Success(_)  => ()

  private def handleResponse(msg: StreamMessage[String, String, String]): Unit =
    val json = msg.payload().get("data")
    Try(objectMapper.readValue(json, classOf[EventEnvelope])) match
      case Success(envelope) =>
        envelope.correlationId.flatMap(id => Option(pending.remove(id))).foreach { future =>
          Try(objectMapper.treeToValue(envelope.payload, classOf[GameCreationResponseDto])) match
            case Success(resp) => future.complete(resp)
            case Failure(ex)   => future.completeExceptionally(ex)
        }
      case Failure(ex) => log.warnf(ex, "Unparseable game-creation response: %s", json)
    ack(msg.id())

  private def ack(id: String): Unit =
    Try(redis.stream(classOf[String]).xack(responseStream, groupName, id)) match
      case Failure(ex) => log.warnf(ex, "Failed to ack response %s", id)
      case Success(_)  => ()

  private def publish(key: String, envelope: EventEnvelope): Unit =
    val json = objectMapper.writeValueAsString(envelope)
    redis
      .stream(classOf[String])
      .xadd(key, new XAddArgs().maxlen(maxStreamLen).nearlyExactTrimming(), Map("data" -> json).asJava)
    ()
