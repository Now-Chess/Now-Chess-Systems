package de.nowchess.chess.resource

import de.nowchess.api.board.Color
import de.nowchess.api.dto.*
import de.nowchess.api.game.{CorrespondenceClockState, DrawReason, GameResult, LiveClockState, WinReason}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.api.player.PlayerInfo
import de.nowchess.chess.grpc.IoGrpcClientWrapper
import de.nowchess.chess.registry.GameEntry
import java.time.Instant

object GameDtoMapper:

  def statusOf(entry: GameEntry): String =
    if entry.engine.pendingTakebackRequestBy.isDefined then "takebackRequested"
    else if entry.engine.pendingDrawOfferBy.isDefined then "drawOffered"
    else
      val ctx = entry.engine.context
      ctx.result match
        case Some(GameResult.Win(_, WinReason.Checkmate))           => "checkmate"
        case Some(GameResult.Win(_, WinReason.Resignation))         => "resign"
        case Some(GameResult.Win(_, WinReason.TimeControl))         => "timeout"
        case Some(GameResult.Draw(DrawReason.Stalemate))            => "stalemate"
        case Some(GameResult.Draw(DrawReason.InsufficientMaterial)) => "insufficientMaterial"
        case Some(GameResult.Draw(_))                               => "draw"
        case None =>
          if ctx.halfMoveClock >= 100 then "fiftyMoveAvailable"
          else if entry.engine.ruleSet.isCheck(ctx) then "check"
          else "started"

  def moveToUci(move: Move): String =
    val base = s"${move.from}${move.to}"
    move.moveType match
      case MoveType.Promotion(PromotionPiece.Queen)  => s"${base}q"
      case MoveType.Promotion(PromotionPiece.Rook)   => s"${base}r"
      case MoveType.Promotion(PromotionPiece.Bishop) => s"${base}b"
      case MoveType.Promotion(PromotionPiece.Knight) => s"${base}n"
      case _                                         => base

  def toPlayerDto(info: PlayerInfo): PlayerInfoDto =
    PlayerInfoDto(info.id.value, info.displayName, info.playerType)

  def toClockDto(entry: GameEntry): Option[ClockDto] =
    val now = Instant.now()
    entry.engine.currentClockState.map {
      case cs: LiveClockState =>
        ClockDto(cs.remainingMs(Color.White, now), cs.remainingMs(Color.Black, now))
      case cs: CorrespondenceClockState =>
        val remaining = cs.remainingMs(cs.activeColor, now)
        ClockDto(
          whiteRemainingMs = if cs.activeColor == Color.White then remaining else -1L,
          blackRemainingMs = if cs.activeColor == Color.Black then remaining else -1L,
        )
    }

  def toGameStateDto(entry: GameEntry, ioClient: IoGrpcClientWrapper): GameStateDto =
    val ctx      = entry.engine.context
    val exported = ioClient.exportCombined(ctx)
    GameStateDto(
      fen = exported.fen,
      pgn = exported.pgn,
      turn = ctx.turn.label.toLowerCase,
      status = statusOf(entry),
      winner = ctx.result.collect { case GameResult.Win(c, _) => c.label.toLowerCase },
      moves = ctx.moves.map(moveToUci),
      undoAvailable = entry.engine.canUndo,
      redoAvailable = entry.engine.canRedo,
      clock = toClockDto(entry),
      takebackRequestedBy = entry.engine.pendingTakebackRequestBy.map(_.label.toLowerCase),
    )

  def toGameFullDto(entry: GameEntry, ioClient: IoGrpcClientWrapper): GameFullDto =
    GameFullDto(entry.gameId, toPlayerDto(entry.white), toPlayerDto(entry.black), toGameStateDto(entry, ioClient))
