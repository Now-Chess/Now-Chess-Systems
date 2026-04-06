package de.nowchess.ui.utils

import de.nowchess.api.board.{Color, Piece, PieceType}

extension (p: Piece)
  def unicode: String = (p.color, p.pieceType) match
    case (Color.White, PieceType.King)   => "\u2654"
    case (Color.White, PieceType.Queen)  => "\u2655"
    case (Color.White, PieceType.Rook)   => "\u2656"
    case (Color.White, PieceType.Bishop) => "\u2657"
    case (Color.White, PieceType.Knight) => "\u2658"
    case (Color.White, PieceType.Pawn)   => "\u2659"
    case (Color.Black, PieceType.King)   => "\u265A"
    case (Color.Black, PieceType.Queen)  => "\u265B"
    case (Color.Black, PieceType.Rook)   => "\u265C"
    case (Color.Black, PieceType.Bishop) => "\u265D"
    case (Color.Black, PieceType.Knight) => "\u265E"
    case (Color.Black, PieceType.Pawn)   => "\u265F"
