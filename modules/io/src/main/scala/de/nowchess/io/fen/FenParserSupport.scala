package de.nowchess.io.fen

import de.nowchess.api.board.*

private[fen] object FenParserSupport:

  sealed trait RankToken
  case class PieceToken(piece: Piece) extends RankToken
  case class EmptyToken(count: Int)   extends RankToken

  val charToPieceType: Map[Char, PieceType] = Map(
    'p' -> PieceType.Pawn,
    'r' -> PieceType.Rook,
    'n' -> PieceType.Knight,
    'b' -> PieceType.Bishop,
    'q' -> PieceType.Queen,
    'k' -> PieceType.King,
  )

  def buildSquares(rank: Rank, tokens: Seq[RankToken]): Option[List[(Square, Piece)]] =
    tokens
      .foldLeft(Option((List.empty[(Square, Piece)], 0))):
        case (None, _) => None
        case (Some((acc, fileIdx)), PieceToken(piece)) =>
          if fileIdx > 7 then None
          else
            val sq = Square(File.values(fileIdx), rank)
            Some((acc :+ (sq -> piece), fileIdx + 1))
        case (Some((acc, fileIdx)), EmptyToken(n)) =>
          val next = fileIdx + n
          if next > 8 then None
          else Some((acc, next))
      .flatMap { case (squares, total) => if total == 8 then Some(squares) else None }
