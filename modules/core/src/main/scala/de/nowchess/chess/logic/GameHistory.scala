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

/** Complete game history: ordered list of moves plus the half-move clock for the 50-move rule.
 *
 *  @param moves         moves played so far, oldest first
 *  @param halfMoveClock plies since the last pawn move or capture (FIDE 50-move rule counter)
 */
case class GameHistory(moves: List[HistoryMove] = List.empty, halfMoveClock: Int = 0):

  /** Add a raw HistoryMove record. Clock increments by 1.
   *  Use the coordinate overload when you know whether the move is a pawn move or capture.
   */
  def addMove(move: HistoryMove): GameHistory =
    GameHistory(moves :+ move, halfMoveClock + 1)

  /** Add a move by coordinates.
   *
   *  @param wasPawnMove true when the moving piece is a pawn — resets the clock to 0
   *  @param wasCapture  true when a piece was captured (including en passant) — resets the clock to 0
   *
   *  If neither flag is set the clock increments by 1.
   */
  def addMove(
    from: Square,
    to: Square,
    castleSide: Option[CastleSide] = None,
    promotionPiece: Option[PromotionPiece] = None,
    wasPawnMove: Boolean = false,
    wasCapture: Boolean = false
  ): GameHistory =
    val newClock = if wasPawnMove || wasCapture then 0 else halfMoveClock + 1
    GameHistory(moves :+ HistoryMove(from, to, castleSide, promotionPiece), newClock)

object GameHistory:
  val empty: GameHistory = GameHistory()
