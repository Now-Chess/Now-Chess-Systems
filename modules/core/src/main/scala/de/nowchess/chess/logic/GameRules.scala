package de.nowchess.chess.logic

import de.nowchess.api.board.*

enum PositionStatus:
  case Normal, InCheck, Mated, Drawn

object GameRules:

  /** True if `color`'s king is under attack on this board. */
  def isInCheck(board: Board, color: Color): Boolean =
    board.pieces
      .collectFirst { case (sq, p) if p.color == color && p.pieceType == PieceType.King => sq }
      .exists { kingSq =>
        board.pieces.exists { case (sq, piece) =>
          piece.color != color &&
          MoveValidator.legalTargets(board, sq).contains(kingSq)
        }
      }

  /** All (from, to) moves for `color` that do not leave their own king in check. */
  def legalMoves(board: Board, color: Color): Set[(Square, Square)] =
    board.pieces
      .collect { case (from, piece) if piece.color == color => from }
      .flatMap { from =>
        MoveValidator.legalTargets(board, from)
          .filter { to =>
            val (newBoard, _) = board.withMove(from, to)
            !isInCheck(newBoard, color)
          }
          .map(to => from -> to)
      }
      .toSet

  /** Position status for the side whose turn it is (`color`). */
  def gameStatus(board: Board, color: Color): PositionStatus =
    val moves   = legalMoves(board, color)
    val inCheck = isInCheck(board, color)
    if moves.isEmpty && inCheck then PositionStatus.Mated
    else if moves.isEmpty       then PositionStatus.Drawn
    else if inCheck             then PositionStatus.InCheck
    else                             PositionStatus.Normal
