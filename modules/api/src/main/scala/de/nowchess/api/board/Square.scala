package de.nowchess.api.board

/**
 * A file (column) on the chess board, a–h.
 * Ordinal values 0–7 correspond to a–h.
 */
enum File:
  case A, B, C, D, E, F, G, H

/**
 * A rank (row) on the chess board, 1–8.
 * Ordinal values 0–7 correspond to ranks 1–8.
 */
enum Rank:
  case R1, R2, R3, R4, R5, R6, R7, R8

/**
 * A unique square on the board, identified by its file and rank.
 *
 * @param file the column, a–h
 * @param rank the row, 1–8
 */
final case class Square(file: File, rank: Rank):
  /** Algebraic notation string, e.g. "e4". */
  override def toString: String =
    s"${file.toString.toLowerCase}${rank.ordinal + 1}"

object Square:
  /** Parse a square from algebraic notation (e.g. "e4").
   *  Returns None if the input is not a valid square name. */
  def fromAlgebraic(s: String): Option[Square] =
    if s.length != 2 then None
    else
      val fileChar = s.charAt(0)
      val rankChar = s.charAt(1)
      val fileOpt = File.values.find(_.toString.equalsIgnoreCase(fileChar.toString))
      val rankOpt =
        rankChar.toString.toIntOption.flatMap(n =>
          if n >= 1 && n <= 8 then Some(Rank.values(n - 1)) else None
        )
      for f <- fileOpt; r <- rankOpt yield Square(f, r)

  val all: IndexedSeq[Square] =
    for
      r <- Rank.values.toIndexedSeq
      f <- File.values.toIndexedSeq
    yield Square(f, r)

  /** Compute a target square by offsetting file and rank.
   *  Returns None if the resulting square is outside the board (0-7 range). */
  extension (sq: Square)
    def offset(fileDelta: Int, rankDelta: Int): Option[Square] =
      val newFileOrd = sq.file.ordinal + fileDelta
      val newRankOrd = sq.rank.ordinal + rankDelta
      if newFileOrd >= 0 && newFileOrd < 8 && newRankOrd >= 0 && newRankOrd < 8 then
        Some(Square(File.values(newFileOrd), Rank.values(newRankOrd)))
      else None