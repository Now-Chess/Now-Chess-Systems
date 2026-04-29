package de.nowchess.chess.redis

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.dto.{GameStateEventDto, GameWritebackEventDto}
import de.nowchess.api.game.{CorrespondenceClockState, LiveClockState}
import de.nowchess.chess.grpc.IoGrpcClientWrapper
import de.nowchess.api.game.{DrawReason, GameResult, WinReason}
import de.nowchess.api.board.Color
import de.nowchess.chess.observer.{GameEvent, Observer}
import de.nowchess.chess.registry.GameRegistry
import de.nowchess.chess.resource.GameDtoMapper
import io.quarkus.redis.datasource.RedisDataSource

class GameRedisPublisher(
    gameId: String,
    registry: GameRegistry,
    redis: RedisDataSource,
    objectMapper: ObjectMapper,
    s2cTopicName: String,
    writebackEmit: String => Unit,
    ioClient: IoGrpcClientWrapper,
    onGameOver: String => Unit,
) extends Observer:

  def onGameEvent(event: GameEvent): Unit =
    registry.get(gameId).foreach { entry =>
      val dto  = GameDtoMapper.toGameStateDto(entry, ioClient)
      val json = objectMapper.writeValueAsString(GameStateEventDto(dto))
      redis.pubsub(classOf[String]).publish(s2cTopicName, json)

      val clock = entry.engine.currentClockState
      val wb = GameWritebackEventDto(
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
          case de.nowchess.api.game.TimeControl.Clock(l, _) => Some(l); case _ => None
        },
        incrementSeconds = entry.engine.timeControl match {
          case de.nowchess.api.game.TimeControl.Clock(_, i) => Some(i); case _ => None
        },
        daysPerMove = entry.engine.timeControl match {
          case de.nowchess.api.game.TimeControl.Correspondence(d) => Some(d); case _ => None
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
      writebackEmit(objectMapper.writeValueAsString(wb))
      if entry.engine.context.result.isDefined then onGameOver(gameId)
    }
