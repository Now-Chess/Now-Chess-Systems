package de.nowchess.bot.service

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.event.EventEnvelope
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.bot.BotController
import de.nowchess.bot.BotDifficulty
import de.nowchess.bot.client.{AccountServiceClient, SyncOfficialBotsRequest}
import de.nowchess.bot.config.RedisConfig
import de.nowchess.io.fen.FenParser
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.stream.{StreamMessage, XAddArgs, XGroupCreateArgs, XReadGroupArgs}
import io.quarkus.runtime.StartupEvent
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import org.eclipse.microprofile.context.ManagedExecutor
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}
import java.time.Duration
import java.util.UUID
import io.quarkus.redis.datasource.pubsub.PubSubCommands
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import java.util.function.Consumer

@ApplicationScoped
class OfficialBotService:

  private val log = Logger.getLogger(classOf[OfficialBotService])

  // scalafix:off DisableSyntax.var
  @Inject var redis: RedisDataSource       = uninitialized
  @Inject var redisConfig: RedisConfig     = uninitialized
  @Inject var objectMapper: ObjectMapper   = uninitialized
  @Inject var botController: BotController = uninitialized
  @Inject var meterRegistry: MeterRegistry = uninitialized
  @Inject var executor: ManagedExecutor    = uninitialized

  @Inject
  @RestClient
  var accountServiceClient: AccountServiceClient = uninitialized
  // scalafix:on DisableSyntax.var

  private val terminalStatuses =
    Set("checkmate", "resign", "timeout", "stalemate", "insufficientMaterial", "draw")

  private val groupName     = "official-bot"
  private val gameOverGroup = "official-bots-game-over"
  private val consumerId    = UUID.randomUUID().toString
  private val maxRetries    = 3
  private val maxStreamLen  = 1000L

  private def eventStream(botName: String): String = s"${redisConfig.prefix}:bot:$botName:events:stream"
  private def gameOverStream: String               = s"${redisConfig.prefix}:game-over"
  private def dlqStream: String                    = s"${redisConfig.prefix}:dlq"

  private val gameWatches = new ConcurrentHashMap[String, (String, PubSubCommands.RedisSubscriber)]()

  @PostConstruct
  def initializeMetrics(): Unit =
    BotController.listBots.foreach { bot =>
      meterRegistry.timer("nowchess.bot.move.duration", "bot", bot).record(0L, TimeUnit.MILLISECONDS)
      meterRegistry.counter("nowchess.bot.moves.computed", "bot", bot).increment(0)
    }

  def onStart(@Observes event: StartupEvent): Unit =
    val bots = BotController.listBots
    try accountServiceClient.syncBots(SyncOfficialBotsRequest(bots))
    catch case ex: Exception => log.errorf(ex, "Failed to auto-register official bots with account service")
    bots.foreach(subscribeToEventChannel)
    subscribeToGameOverStream()

  private def subscribeToEventChannel(botName: String): Unit =
    createGroupIfAbsent(botName)
    executor.submit(
      new Runnable:
        def run(): Unit = pollLoop(botName),
    )
    log.infof("Listening to bot event stream for %s (consumer=%s)", botName, consumerId)

  private def createGroupIfAbsent(botName: String): Unit =
    Try(
      redis
        .stream(classOf[String])
        .xgroupCreate(eventStream(botName), groupName, "0", new XGroupCreateArgs().mkstream()),
    ) match
      case Failure(ex) if Option(ex.getMessage).exists(_.contains("BUSYGROUP")) => ()
      case Failure(ex) => log.warnf(ex, "Failed to create bot event consumer group for %s", botName)
      case Success(_)  => ()

  private def pollLoop(botName: String): Unit =
    while true do
      Try {
        val messages = redis
          .stream(classOf[String])
          .xreadgroup(
            groupName,
            consumerId,
            eventStream(botName),
            ">",
            new XReadGroupArgs().count(10).block(Duration.ofSeconds(2)),
          )
        Option(messages).foreach(_.forEach(msg => handleStreamMessage(botName, msg)))
      } match
        case Failure(ex) => log.warnf(ex, "Error in bot event poll loop for %s", botName)
        case Success(_)  => ()

  private def handleStreamMessage(botName: String, msg: StreamMessage[String, String, String]): Unit =
    val json    = msg.payload().get("data")
    val attempt = Option(msg.payload().get("attempt")).flatMap(_.toIntOption).getOrElse(0)
    Try {
      val envelope = objectMapper.readValue(json, classOf[EventEnvelope])
      handleBotEvent(botName, envelope)
    } match
      case Success(_) => ack(botName, msg.id())
      case Failure(ex) if attempt + 1 < maxRetries =>
        log.warnf(ex, "Bot event handling failed for %s (attempt %d), retrying", botName, attempt)
        retry(botName, json, attempt + 1)
        ack(botName, msg.id())
      case Failure(ex) =>
        log.errorf(ex, "Bot event handling failed for %s after %d attempts, sending to DLQ", botName, maxRetries)
        toDlq(json, ex, attempt)
        ack(botName, msg.id())

  private def handleBotEvent(botName: String, envelope: EventEnvelope): Unit =
    val payload      = envelope.payload
    val gameId       = payload.path("gameId").asText()
    val playingAs    = payload.path("playingAs").asText()
    val difficulty   = payload.path("difficulty").asInt(1400)
    val botAccountId = payload.path("botAccountId").asText()
    watchGame(botName, gameId, playingAs, difficulty, botAccountId)

  private def ack(botName: String, id: String): Unit =
    Try(redis.stream(classOf[String]).xack(eventStream(botName), groupName, id)) match
      case Failure(ex) => log.warnf(ex, "Failed to ack bot event %s", id)
      case Success(_)  => ()

  private def retry(botName: String, json: String, attempt: Int): Unit =
    xadd(eventStream(botName), Map("data" -> json, "attempt" -> attempt.toString))

  private def toDlq(json: String, error: Throwable, attempt: Int): Unit =
    xadd(
      dlqStream,
      Map(
        "data"      -> json,
        "eventType" -> "BotGameStart",
        "error"     -> Option(error.getMessage).getOrElse(error.getClass.getName),
        "attempt"   -> (attempt + 1).toString,
      ),
    )

  private def xadd(key: String, fields: Map[String, String]): Unit =
    Try(
      redis
        .stream(classOf[String])
        .xadd(key, new XAddArgs().maxlen(maxStreamLen).nearlyExactTrimming(), fields.asJava),
    ) match
      case Failure(ex) => log.errorf(ex, "Failed to publish to stream %s", key)
      case Success(_)  => ()

  private def watchGame(
      botName: String,
      gameId: String,
      playingAs: String,
      difficulty: Int,
      botAccountId: String,
  ): Unit =
    val handler: Consumer[String] = msg => handleGameEvent(botName, gameId, playingAs, difficulty, botAccountId, msg)
    val subscriber = redis.pubsub(classOf[String]).subscribe(s"${redisConfig.prefix}:game:$gameId:s2c", handler)
    gameWatches.put(gameId, (botName, subscriber))
    ()

  private def subscribeToGameOverStream(): Unit =
    Try(
      redis
        .stream(classOf[String])
        .xgroupCreate(gameOverStream, gameOverGroup, "$", new XGroupCreateArgs().mkstream()),
    ) match
      case Failure(ex) if Option(ex.getMessage).exists(_.contains("BUSYGROUP")) => ()
      case Failure(ex) => log.warnf(ex, "Failed to create game-over consumer group")
      case Success(_)  => ()
    executor.submit(
      new Runnable:
        def run(): Unit = gameOverPollLoop(),
    )
    log.infof("Listening to game-over stream (consumer=%s)", consumerId)

  private def gameOverPollLoop(): Unit =
    while true do
      Try {
        val messages = redis
          .stream(classOf[String])
          .xreadgroup(
            gameOverGroup,
            consumerId,
            gameOverStream,
            ">",
            new XReadGroupArgs().count(10).block(Duration.ofSeconds(2)),
          )
        Option(messages).foreach(_.forEach(msg => handleGameOverMessage(msg)))
      } match
        case Failure(ex) => log.warnf(ex, "Error in game-over poll loop")
        case Success(_)  => ()

  private def handleGameOverMessage(msg: StreamMessage[String, String, String]): Unit =
    val json    = msg.payload().get("data")
    val attempt = Option(msg.payload().get("attempt")).flatMap(_.toIntOption).getOrElse(0)
    Try {
      val node   = objectMapper.readTree(json)
      val gameId = node.path("payload").path("gameId").asText()
      if gameId.nonEmpty then
        Option(gameWatches.remove(gameId)).foreach { (botName, subscriber) =>
          val topic = s"${redisConfig.prefix}:game:$gameId:s2c"
          Try(subscriber.unsubscribe(topic)) match
            case Failure(ex) => log.warnf(ex, "Failed to unsubscribe from game %s", gameId)
            case Success(_)  => log.infof("Bot %s cleaned up game %s after GameOver", botName, gameId)
        }
    } match
      case Success(_) =>
        ackGameOver(msg.id())
      case Failure(ex) if attempt + 1 < maxRetries =>
        log.warnf(ex, "GameOver handling failed (attempt %d), retrying", attempt)
        xadd(gameOverStream, Map("data" -> json, "attempt" -> (attempt + 1).toString))
        ackGameOver(msg.id())
      case Failure(ex) =>
        log.errorf(ex, "GameOver handling failed after %d attempts, sending to DLQ", maxRetries)
        xadd(
          dlqStream,
          Map(
            "data"      -> json,
            "eventType" -> "GameOver",
            "error"     -> Option(ex.getMessage).getOrElse(ex.getClass.getName),
            "attempt"   -> attempt.toString,
          ),
        )
        ackGameOver(msg.id())

  private def ackGameOver(id: String): Unit =
    Try(redis.stream(classOf[String]).xack(gameOverStream, gameOverGroup, id)) match
      case Failure(ex) => log.warnf(ex, "Failed to ack game-over message %s", id)
      case Success(_)  => ()

  private def handleGameEvent(
      botName: String,
      gameId: String,
      playingAs: String,
      difficulty: Int,
      botAccountId: String,
      msg: String,
  ): Unit =
    try
      val node   = objectMapper.readTree(msg)
      val status = node.path("state").path("status").asText("")
      if !terminalStatuses.contains(status) then
        val turn = node.path("state").path("turn").asText("")
        if turn == playingAs then
          val fen = node.path("state").path("fen").asText()
          computeAndSendMove(botName, gameId, fen, difficulty, botAccountId)
    catch case _: Exception => ()

  private def computeAndSendMove(
      botName: String,
      gameId: String,
      fen: String,
      difficulty: Int,
      botAccountId: String,
  ): Unit =
    val level = DifficultyMapper.fromElo(difficulty).getOrElse(BotDifficulty.Medium)
    botController.getBot(botName).orElse(botController.getBot(level.toString.toLowerCase)).foreach { bot =>
      FenParser.parseFen(fen).toOption.foreach { context =>
        val timer   = meterRegistry.timer("nowchess.bot.move.duration", "bot", botName)
        val moveOpt = timer.recordCallable[Option[Move]](() => bot(context))
        moveOpt.foreach { move =>
          meterRegistry.counter("nowchess.bot.moves.computed", "bot", botName).increment()
          val uci      = toUci(move)
          val c2sTopic = s"${redisConfig.prefix}:game:$gameId:c2s"
          val moveMsg  = s"""{"type":"MOVE","uci":"$uci","playerId":"$botAccountId"}"""
          redis.pubsub(classOf[String]).publish(c2sTopic, moveMsg)
          ()
        }
      }
    }

  private def toUci(move: Move): String =
    val base = s"${move.from}${move.to}"
    move.moveType match
      case MoveType.Promotion(piece) => base + promotionChar(piece)
      case _                         => base

  private def promotionChar(piece: PromotionPiece): String =
    piece match
      case PromotionPiece.Knight => "n"
      case PromotionPiece.Bishop => "b"
      case PromotionPiece.Rook   => "r"
      case PromotionPiece.Queen  => "q"
