package de.nowchess.chess

object Parser:

  /** Parses coordinate notation such as "e2e4" or "g1f3".
   *  Returns None for any input that does not match the expected format.
   */
  def parseMove(input: String): Option[(Square, Square)] =
    val trimmed = input.trim.toLowerCase
    Option.when(trimmed.length == 4)(trimmed).flatMap: s =>
      for
        from <- parseSquare(s.substring(0, 2))
        to   <- parseSquare(s.substring(2, 4))
      yield (from, to)

  private def parseSquare(s: String): Option[Square] =
    Option.when(s.length == 2)(s).flatMap: sq =>
      val file = sq(0) - 'a'
      val rank = sq(1) - '1'
      Option.when(file >= 0 && file <= 7 && rank >= 0 && rank <= 7)(Square(file, rank))
