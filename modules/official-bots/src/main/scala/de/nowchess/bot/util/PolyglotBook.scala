package de.nowchess.bot.util

import de.nowchess.api.board.*
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}

import java.io.{DataInputStream, FileInputStream}
import scala.collection.mutable
import scala.util.Random

/** Reads a Polyglot opening book (.bin file) and probes it for moves.
  *
  * Polyglot books are binary files with 16-byte big-endian records:
  *   - key: 8 bytes (Long) — Zobrist hash of the position
  *   - move: 2 bytes (Short) — packed as (to_file | to_rank | from_file | from_rank | promotion)
  *   - weight: 2 bytes (Short) — move weight (higher = preferred)
  *   - learn: 4 bytes (Int) — learning data (unused)
  */
final class PolyglotBook(path: String):

  private val entries: Map[Long, Vector[BookEntry]] =
    try {
      val r = loadBookFile(path)
      println(s"Book loaded successfully. ${r.size} entries found.")
      r
    } catch
      case e: Exception =>
        println(s"Error loading book: $e")
        // Gracefully fail: return empty map if book cannot be loaded
        // This allows the bot to work even if the book file is missing
        scala.collection.immutable.Map.empty

  /** Probe the book for a move in the given position. Returns a weighted random move, or None if not in book. */
  def probe(context: GameContext): Option[Move] =
    val hash = PolyglotHash.hash(context)
    println(f"0x$hash%016X")
    entries.get(hash).flatMap { bookEntries =>
      if bookEntries.isEmpty then None
      else
        val entry = weightedRandom(bookEntries)
        decodeMove(entry.move, context)
    }

  private def loadBookFile(path: String): Map[Long, Vector[BookEntry]] =
    val input = DataInputStream(FileInputStream(path))
    try
      val result = mutable.Map[Long, Vector[BookEntry]]()
      while input.available() > 0 do
        val key    = input.readLong()
        val move   = input.readShort()
        val weight = input.readShort()
        input.readInt() // learning data (unused)

        val entry = BookEntry(key, move, weight)
        result.updateWith(key) {
          case Some(entries) => Some(entries :+ entry)
          case None          => Some(Vector(entry))
        }
      result.toMap
    finally input.close()

  /** Decode a packed Polyglot move short into an Option[Move].
    *
    * Bit layout of the move Short:
    *   - bits 0-2: to_file (0-7)
    *   - bits 3-5: to_rank (0-7)
    *   - bits 6-8: from_file (0-7)
    *   - bits 9-11: from_rank (0-7)
    *   - bits 12-14: promotion piece (0=none, 1=knight, 2=bishop, 3=rook, 4=queen)
    */
  private def decodeMove(raw: Short, context: GameContext): Option[Move] =
    val toFile        = raw & 0x07
    val toRank        = (raw >> 3) & 0x07
    val fromFile      = (raw >> 6) & 0x07
    val fromRank      = (raw >> 9) & 0x07
    val promotionBits = (raw >> 12) & 0x07

    if toFile > 7 || toRank > 7 || fromFile > 7 || fromRank > 7 then None
    else
      val from = Square(File.values(fromFile), Rank.values(fromRank))
      val to   = Square(File.values(toFile), Rank.values(toRank))

      if isKingMove(context, from) && isRookSquare(to, context) then Some(decodeCastling(from, to))
      else
        val moveTypeOpt: Option[MoveType] =
          if promotionBits > 0 then
            promotionBits match
              case 1 => Some(MoveType.Promotion(PromotionPiece.Knight))
              case 2 => Some(MoveType.Promotion(PromotionPiece.Bishop))
              case 3 => Some(MoveType.Promotion(PromotionPiece.Rook))
              case 4 => Some(MoveType.Promotion(PromotionPiece.Queen))
              case _ => None
          else Some(MoveType.Normal(context.board.pieces.contains(to)))

        moveTypeOpt.map(moveType => Move(from, to, moveType))

  private def isKingMove(context: GameContext, square: Square): Boolean =
    context.board.pieces.get(square).exists { piece =>
      piece.pieceType == PieceType.King
    }

  private def isRookSquare(square: Square, context: GameContext): Boolean =
    context.board.pieces.get(square).exists { piece =>
      piece.pieceType == PieceType.Rook
    }

  /** Decode castling from king-to-rook square to the standard move.
    *
    * Polyglot encodes castling as:
    *   - e1→h1 = White kingside (move to g1)
    *   - e1→a1 = White queenside (move to c1)
    *   - e8→h8 = Black kingside (move to g8)
    *   - e8→a8 = Black queenside (move to c8)
    */
  private def decodeCastling(from: Square, to: Square): Move =
    if to.file == File.H then Move(from, Square(File.G, to.rank), MoveType.CastleKingside)
    else if to.file == File.A then Move(from, Square(File.C, to.rank), MoveType.CastleQueenside)
    else
      // Fallback (should not happen in a valid book)
      Move(from, to, MoveType.Normal())

  /** Select a weighted random move from the list of book entries. */
  private def weightedRandom(entries: Vector[BookEntry]): BookEntry =
    if entries.length == 1 then entries.head
    else
      val totalWeight = entries.map(_.weight).sum
      val pick        = Random.nextInt(totalWeight.max(1)) // NOSONAR

      @scala.annotation.tailrec
      def select(remaining: Int, idx: Int): BookEntry =
        if idx >= entries.length then entries.last
        else if remaining < entries(idx).weight then entries(idx)
        else select(remaining - entries(idx).weight, idx + 1)

      select(pick, 0)

private case class BookEntry(key: Long, move: Short, weight: Int)
