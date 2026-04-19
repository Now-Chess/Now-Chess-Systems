package de.nowchess.chess.controller

import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.api.move.PromotionPiece

object Parser:

  /** Parses UCI move notation: "e2e4" (4 chars) or "e7e8q" (5 chars with promotion piece suffix). The promotion suffix
    * is q=Queen, r=Rook, b=Bishop, n=Knight. Returns None for invalid input.
    */
  def parseMove(input: String): Option[(Square, Square, Option[PromotionPiece])] =
    val trimmed = input.trim.toLowerCase
    trimmed.length match
      case 4 =>
        for
          from <- parseSquare(trimmed.substring(0, 2))
          to   <- parseSquare(trimmed.substring(2, 4))
        yield (from, to, None)
      case 5 =>
        for
          from  <- parseSquare(trimmed.substring(0, 2))
          to    <- parseSquare(trimmed.substring(2, 4))
          promo <- parsePromotion(trimmed(4))
        yield (from, to, Some(promo))
      case _ => None

  private def parseSquare(s: String): Option[Square] =
    Option
      .when(s.length == 2)(s)
      .flatMap: sq =>
        val fileIdx = sq(0) - 'a'
        val rankIdx = sq(1) - '1'
        Option.when(fileIdx >= 0 && fileIdx <= 7 && rankIdx >= 0 && rankIdx <= 7)(
          Square(File.values(fileIdx), Rank.values(rankIdx)),
        )

  private def parsePromotion(c: Char): Option[PromotionPiece] = c match
    case 'q' => Some(PromotionPiece.Queen)
    case 'r' => Some(PromotionPiece.Rook)
    case 'b' => Some(PromotionPiece.Bishop)
    case 'n' => Some(PromotionPiece.Knight)
    case _   => None
