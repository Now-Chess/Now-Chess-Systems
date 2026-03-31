package de.nowchess.chess.logic

import de.nowchess.api.board.Square
import de.nowchess.api.move.PromotionPiece

/** A single move recorded in the game history. Distinct from api.move.Move which represents user intent. */
case class HistoryMove(
  from: Square,
  to: Square,
  castleSide: Option[CastleSide],
  promotionPiece: Option[PromotionPiece] = None
)

/** Complete game history: ordered list of moves. */
case class GameHistory(moves: List[HistoryMove] = List.empty):
  def addMove(move: HistoryMove): GameHistory =
    GameHistory(moves :+ move)

  def addMove(from: Square, to: Square): GameHistory =
    addMove(HistoryMove(from, to, None))

  def addMove(
    from: Square,
    to: Square,
    castleSide: Option[CastleSide] = None,
    promotionPiece: Option[PromotionPiece] = None
  ): GameHistory =
    addMove(HistoryMove(from, to, castleSide, promotionPiece))

object GameHistory:
  val empty: GameHistory = GameHistory()
