package de.nowchess.chess.redis

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import de.nowchess.api.board.Color
import de.nowchess.api.dto.{GameStateDto, GameStateEventDto, GameWritebackEventDto}
import de.nowchess.api.event.{EventEnvelope, EventType, GameOverPayload}
import de.nowchess.api.game.{CorrespondenceClockState, DrawReason, GameResult, LiveClockState, TimeControl, WinReason}
import de.nowchess.chess.grpc.IoGrpcClientWrapper
import de.nowchess.chess.observer.{GameEvent, Observer}
import de.nowchess.chess.registry.{GameEntry, GameRegistry}
import de.nowchess.chess.resource.GameDtoMapper
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.stream.XAddArgs
import org.jboss.logging.Logger
import scala.jdk.CollectionConverters.*

object GameRedisPublisher:
  private val log = Logger.getLogger(classOf[GameRedisPublisher])

class GameRedisPublisher(
    gameId: String,
    registry: GameRegistry,
    redis: RedisDataSource,
    objectMapper: ObjectMapper,
    s2cTopicName: String,
    writebackEmit: String => Unit,
    ioClient: IoGrpcClientWrapper,
    onGameOver: String => Unit,
    redisPrefix: String,
) extends Observer:

  private val maxStreamLen = 1000L

  def emitInitialWriteback(): Unit =
    try
      registry.get(gameId).foreach { entry =>
        val dto = GameDtoMapper.toGameStateDto(entry, ioClient)
        writebackEmit(objectMapper.writeValueAsString(buildWriteback(entry, dto)))
      }
    catch case ex: Exception => GameRedisPublisher.log.warnf(ex, "Failed to emit initial writeback for game %s", gameId)

  def onGameEvent(event: GameEvent): Unit =
    try
      GameRedisPublisher.log.debugf("Publishing game event for game %s", gameId)
      registry.get(gameId).foreach { entry =>
        val dto = GameDtoMapper.toGameStateDto(entry, ioClient)
        redis.pubsub(classOf[String]).publish(s2cTopicName, objectMapper.writeValueAsString(GameStateEventDto(dto)))
        writebackEmit(objectMapper.writeValueAsString(buildWriteback(entry, dto)))
        entry.engine.context.result.foreach { result =>
          publishGameOver(entry, result)
          onGameOver(gameId)
        }
      }
    catch case ex: Exception => GameRedisPublisher.log.warnf(ex, "Failed to publish game event for game %s", gameId)

  private def publishGameOver(entry: GameEntry, result: GameResult): Unit =
    val resultStr = result match
      case GameResult.Win(Color.White, _) => "white"
      case GameResult.Win(Color.Black, _) => "black"
      case GameResult.Draw(_)             => "draw"
    val terminationReason = result match
      case GameResult.Win(_, WinReason.Checkmate)           => "checkmate"
      case GameResult.Win(_, WinReason.Resignation)         => "resignation"
      case GameResult.Win(_, WinReason.TimeControl)         => "timeout"
      case GameResult.Draw(DrawReason.Stalemate)            => "stalemate"
      case GameResult.Draw(DrawReason.InsufficientMaterial) => "insufficient_material"
      case GameResult.Draw(DrawReason.FiftyMoveRule)        => "fifty_move"
      case GameResult.Draw(DrawReason.ThreefoldRepetition)  => "repetition"
      case GameResult.Draw(DrawReason.Agreement)            => "agreement"
    val payload = objectMapper.valueToTree[JsonNode](
      GameOverPayload(gameId, resultStr, terminationReason, entry.white.id.value, entry.black.id.value),
    )
    val envelope = EventEnvelope.of(EventType.GameOver, payload)
    redis
      .stream(classOf[String])
      .xadd(
        s"$redisPrefix:game-over",
        new XAddArgs().maxlen(maxStreamLen).nearlyExactTrimming(),
        Map("data" -> objectMapper.writeValueAsString(envelope)).asJava,
      )

  private def buildWriteback(entry: GameEntry, dto: GameStateDto): GameWritebackEventDto =
    val clock = entry.engine.currentClockState
    GameWritebackEventDto(
      gameId = gameId,
      fen = dto.fen,
      pgn = dto.pgn,
      moveCount = entry.engine.context.moves.size,
      whiteId = entry.white.id.value,
      whiteName = entry.white.displayName,
      blackId = entry.black.id.value,
      blackName = entry.black.displayName,
      mode = entry.mode.toString,
      resigned = entry.resigned,
      limitSeconds = entry.engine.timeControl match {
        case TimeControl.Clock(l, _) => Some(l); case _ => None
      },
      incrementSeconds = entry.engine.timeControl match {
        case TimeControl.Clock(_, i) => Some(i); case _ => None
      },
      daysPerMove = entry.engine.timeControl match {
        case TimeControl.Correspondence(d) => Some(d); case _ => None
      },
      whiteRemainingMs = clock.collect { case c: LiveClockState => c.whiteRemainingMs },
      blackRemainingMs = clock.collect { case c: LiveClockState => c.blackRemainingMs },
      incrementMs = clock.collect { case c: LiveClockState => c.incrementMs },
      clockLastTickAt = clock.collect { case c: LiveClockState => c.lastTickAt.toEpochMilli },
      clockMoveDeadline = clock.collect { case c: CorrespondenceClockState => c.moveDeadline.toEpochMilli },
      clockActiveColor = clock.map(_.activeColor.label.toLowerCase),
      pendingDrawOffer = entry.engine.pendingDrawOfferBy.map(_.label.toLowerCase),
      result = entry.engine.context.result.map {
        case GameResult.Win(Color.White, _) => "white"
        case GameResult.Win(Color.Black, _) => "black"
        case GameResult.Draw(_)             => "draw"
      },
      terminationReason = entry.engine.context.result.map {
        case GameResult.Win(_, WinReason.Checkmate)           => "checkmate"
        case GameResult.Win(_, WinReason.Resignation)         => "resignation"
        case GameResult.Win(_, WinReason.TimeControl)         => "timeout"
        case GameResult.Draw(DrawReason.Stalemate)            => "stalemate"
        case GameResult.Draw(DrawReason.InsufficientMaterial) => "insufficient_material"
        case GameResult.Draw(DrawReason.FiftyMoveRule)        => "fifty_move"
        case GameResult.Draw(DrawReason.ThreefoldRepetition)  => "repetition"
        case GameResult.Draw(DrawReason.Agreement)            => "agreement"
      },
      redoStack = entry.engine.redoStackMoves.map(GameDtoMapper.moveToUci),
      pendingTakebackRequest = entry.engine.pendingTakebackRequestBy.map(_.label.toLowerCase),
    )
