package de.nowchess.rules.config

import de.nowchess.api.board.{CastlingRights, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.api.game.{DrawReason, GameContext, GameResult}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.rules.dto.{ContextMoveRequest, ContextSquareRequest}
import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection(
  targets = Array(
    classOf[ContextSquareRequest],
    classOf[ContextMoveRequest],
    classOf[GameContext],
    classOf[GameResult],
    classOf[DrawReason],
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
  ),
)
class NativeReflectionConfig
