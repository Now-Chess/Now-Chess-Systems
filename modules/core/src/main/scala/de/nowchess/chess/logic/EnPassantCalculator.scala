package de.nowchess.chess.logic

import de.nowchess.api.board.*

object EnPassantCalculator:

  /** Returns the en passant target square if the last move was a double pawn push.
   *  The target is the square the pawn passed through (e.g. e2→e4 yields e3).
   */
  def enPassantTarget(board: Board, history: GameHistory): Option[Square] =
    history.moves.lastOption.flatMap: move =>
      val rankDiff = move.to.rank.ordinal - move.from.rank.ordinal
      val isDoublePush = math.abs(rankDiff) == 2
      val isPawn = board.pieceAt(move.to).exists(_.pieceType == PieceType.Pawn)
      if isDoublePush && isPawn then
        val midRankIdx = move.from.rank.ordinal + rankDiff / 2
        Some(Square(move.to.file, Rank.values(midRankIdx)))
      else None

  /** True if moving from→to is an en passant capture. */
  def isEnPassant(board: Board, history: GameHistory, from: Square, to: Square): Boolean =
    board.pieceAt(from).exists(_.pieceType == PieceType.Pawn) &&
    enPassantTarget(board, history).contains(to) &&
    math.abs(to.file.ordinal - from.file.ordinal) == 1

  /** Returns the square of the pawn to remove when an en passant capture lands on `to`.
   *  White captures upward → captured pawn is one rank below `to`.
   *  Black captures downward → captured pawn is one rank above `to`.
   */
  def capturedPawnSquare(to: Square, color: Color): Square =
    val capturedRankIdx = to.rank.ordinal + (if color == Color.White then -1 else 1)
    Square(to.file, Rank.values(capturedRankIdx))
