package de.nowchess.api.board

/** A chess piece on the board — a combination of a color and a piece type. */
final case class Piece(color: Color, pieceType: PieceType)

object Piece:
  // Convenience constructors
  val WhitePawn:   Piece = Piece(Color.White, PieceType.Pawn)
  val WhiteKnight: Piece = Piece(Color.White, PieceType.Knight)
  val WhiteBishop: Piece = Piece(Color.White, PieceType.Bishop)
  val WhiteRook:   Piece = Piece(Color.White, PieceType.Rook)
  val WhiteQueen:  Piece = Piece(Color.White, PieceType.Queen)
  val WhiteKing:   Piece = Piece(Color.White, PieceType.King)

  val BlackPawn:   Piece = Piece(Color.Black, PieceType.Pawn)
  val BlackKnight: Piece = Piece(Color.Black, PieceType.Knight)
  val BlackBishop: Piece = Piece(Color.Black, PieceType.Bishop)
  val BlackRook:   Piece = Piece(Color.Black, PieceType.Rook)
  val BlackQueen:  Piece = Piece(Color.Black, PieceType.Queen)
  val BlackKing:   Piece = Piece(Color.Black, PieceType.King)
