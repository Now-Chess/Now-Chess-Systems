package de.nowchess.chess.logic

import de.nowchess.api.board.*

enum PositionStatus:
  case Normal, InCheck, Mated, Drawn

object GameRules:

  /** True if `color`'s king is under attack on this board. */
  def isInCheck(board: Board, color: Color): Boolean = false

  /** All (from, to) moves for `color` that do not leave their own king in check. */
  def legalMoves(board: Board, color: Color): Set[(Square, Square)] = Set.empty

  /** Position status for the side whose turn it is (`color`). */
  def gameStatus(board: Board, color: Color): PositionStatus = PositionStatus.Normal
