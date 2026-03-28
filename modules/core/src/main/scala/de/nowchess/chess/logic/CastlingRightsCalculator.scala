package de.nowchess.chess.logic

import de.nowchess.api.board.{Color, File, Rank, Square}
import de.nowchess.api.game.CastlingRights

/** Derives castling rights from move history. */
object CastlingRightsCalculator:

  def deriveCastlingRights(history: GameHistory, color: Color): CastlingRights =
    val (kingRow, kingsideRookFile, queensideRookFile) = color match
      case Color.White => (Rank.R1, File.H, File.A)
      case Color.Black => (Rank.R8, File.H, File.A)

    // Check if king has moved
    val kingHasMoved = history.moves.exists: move =>
      move.from == Square(File.E, kingRow) || move.castleSide.isDefined

    if kingHasMoved then
      CastlingRights.None
    else
      // Check if kingside rook has moved or was captured
      val kingsideLost = history.moves.exists: move =>
        move.from == Square(kingsideRookFile, kingRow) ||
        move.to == Square(kingsideRookFile, kingRow)

      // Check if queenside rook has moved or was captured
      val queensideLost = history.moves.exists: move =>
        move.from == Square(queensideRookFile, kingRow) ||
        move.to == Square(queensideRookFile, kingRow)

      CastlingRights(kingSide = !kingsideLost, queenSide = !queensideLost)
