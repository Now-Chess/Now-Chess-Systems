package de.nowchess.chess.logic

import de.nowchess.api.board.*

enum CastleSide:
  case Kingside, Queenside

extension (b: Board)
  def withCastle(color: Color, side: CastleSide): Board =
    val rank = if color == Color.White then Rank.R1 else Rank.R8
    val kingFrom = Square(File.E, rank)
    val (kingTo, rookFrom, rookTo) = side match
      case CastleSide.Kingside =>
        (Square(File.G, rank), Square(File.H, rank), Square(File.F, rank))
      case CastleSide.Queenside =>
        (Square(File.C, rank), Square(File.A, rank), Square(File.D, rank))

    val king = b.pieceAt(kingFrom).get
    val rook = b.pieceAt(rookFrom).get

    b.removed(kingFrom).removed(rookFrom)
      .updated(kingTo, king)
      .updated(rookTo, rook)
