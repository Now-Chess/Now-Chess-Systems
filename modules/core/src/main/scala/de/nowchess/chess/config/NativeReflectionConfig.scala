package de.nowchess.chess.config

import de.nowchess.api.board.{CastlingRights, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.api.dto.*
import de.nowchess.api.game.{DrawReason, GameContext, GameMode, GameResult}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection(
  targets = Array(
    classOf[ApiErrorDto],
    classOf[ClockDto],
    classOf[CreateGameRequestDto],
    classOf[ErrorEventDto],
    classOf[GameFullDto],
    classOf[GameFullEventDto],
    classOf[GameStateDto],
    classOf[GameStateEventDto],
    classOf[ImportFenRequestDto],
    classOf[ImportPgnRequestDto],
    classOf[LegalMoveDto],
    classOf[LegalMovesResponseDto],
    classOf[OkResponseDto],
    classOf[PlayerInfoDto],
    classOf[TimeControlDto],
    classOf[GameContext],
    classOf[Color],
    classOf[Piece],
    classOf[PieceType],
    classOf[CastlingRights],
    classOf[Square],
    classOf[File],
    classOf[Rank],
    classOf[Move],
    classOf[MoveType],
    classOf[PromotionPiece],
    classOf[GameResult],
    classOf[DrawReason],
    classOf[GameMode],
  ),
)
class NativeReflectionConfig
