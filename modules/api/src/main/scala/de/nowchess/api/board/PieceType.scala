package de.nowchess.api.board

enum PieceType:
  case Pawn, Knight, Bishop, Rook, Queen, King

  def label: String = this match
    case Pawn   => "Pawn"
    case Knight => "Knight"
    case Bishop => "Bishop"
    case Rook   => "Rook"
    case Queen  => "Queen"
    case King   => "King"
