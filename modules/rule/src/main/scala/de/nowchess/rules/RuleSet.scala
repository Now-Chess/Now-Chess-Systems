package de.nowchess.rules

import de.nowchess.api.game.GameContext
import de.nowchess.api.board.Square
import de.nowchess.api.move.Move

/** Extension point for chess rule variants (standard, Chess960, etc.).
 *  All rule queries are stateless: given a GameContext, return the answer.
 */
trait RuleSet:
  /** All pseudo-legal moves for the piece on `square` (ignores check). */
  def candidateMoves(context: GameContext, square: Square): List[Move]

  /** Legal moves for `square`: candidates that don't leave own king in check. */
  def legalMoves(context: GameContext, square: Square): List[Move]

  /** All legal moves for the side to move. */
  def allLegalMoves(context: GameContext): List[Move]

  /** True if the side to move's king is in check. */
  def isCheck(context: GameContext): Boolean

  /** True if the side to move is in check and has no legal moves. */
  def isCheckmate(context: GameContext): Boolean

  /** True if the side to move is not in check and has no legal moves. */
  def isStalemate(context: GameContext): Boolean

  /** True if neither side has enough material to checkmate. */
  def isInsufficientMaterial(context: GameContext): Boolean

  /** True if halfMoveClock >= 100 (50-move rule). */
  def isFiftyMoveRule(context: GameContext): Boolean

  /** Apply a legal move to produce the next game context.
   *  Handles all special move types: castling, en passant, promotion.
   *  Updates castling rights, en passant square, half-move clock, turn, and move history.
   */
  def applyMove(context: GameContext, move: Move): GameContext
