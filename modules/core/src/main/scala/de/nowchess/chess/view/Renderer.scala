package de.nowchess.chess.view

import de.nowchess.api.board.{Board, Color, File, Rank, Square}

object Renderer:

  private val AnsiReset       = "\u001b[0m"
  private val AnsiLightSquare = "\u001b[48;5;223m"  // warm beige
  private val AnsiDarkSquare  = "\u001b[48;5;130m"  // brown
  private val AnsiWhitePiece  = "\u001b[97m"         // bright white text
  private val AnsiBlackPiece  = "\u001b[30m"         // black text

  def render(board: Board): String =
    val rows = (0 until 8).reverse.map { rank =>
      val cells = (0 until 8).map { file =>
        val sq        = Square(File.values(file), Rank.values(rank))
        val isLightSq = (file + rank) % 2 != 0
        val bgColor   = if isLightSq then AnsiLightSquare else AnsiDarkSquare
        board.pieceAt(sq) match
          case Some(piece) =>
            val fgColor = if piece.color == Color.White then AnsiWhitePiece else AnsiBlackPiece
            s"$bgColor$fgColor ${piece.unicode} $AnsiReset"
          case None =>
            s"$bgColor   $AnsiReset"
      }.mkString
      s"${rank + 1} $cells ${rank + 1}"
    }.mkString("\n")
    s"  a  b  c  d  e  f  g  h\n$rows\n  a  b  c  d  e  f  g  h\n"
