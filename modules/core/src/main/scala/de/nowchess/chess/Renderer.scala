package de.nowchess.chess

object Renderer:

  private val AnsiReset       = "\u001b[0m"
  private val AnsiLightSquare = "\u001b[48;5;223m"  // warm beige
  private val AnsiDarkSquare  = "\u001b[48;5;130m"  // brown
  private val AnsiWhitePiece  = "\u001b[97m"         // bright white text
  private val AnsiBlackPiece  = "\u001b[30m"         // black text

  def render(board: Board): String =
    val sb = new StringBuilder
    sb.append("  a  b  c  d  e  f  g  h\n")
    for rank <- (0 until 8).reverse do
      sb.append(s"${rank + 1} ")
      for file <- 0 until 8 do
        val sq          = Square(file, rank)
        val isLightSq   = (file + rank) % 2 != 0
        val bgColor     = if isLightSq then AnsiLightSquare else AnsiDarkSquare
        val cellContent = board.pieceAt(sq) match
          case Some(piece) =>
            val fgColor = if piece.color == Color.White then AnsiWhitePiece else AnsiBlackPiece
            s"$bgColor$fgColor ${piece.unicode} $AnsiReset"
          case None =>
            s"$bgColor   $AnsiReset"
        sb.append(cellContent)
      sb.append(s" ${rank + 1}\n")
    sb.append("  a  b  c  d  e  f  g  h\n")
    sb.toString
