package de.nowchess.chess.controller

import de.nowchess.api.board.{File, Rank, Square}

object Parser:

  /** Parses coordinate notation such as "e2e4" or "g1f3". Returns None for any input that does not match the expected
    * format.
    */
  def parseMove(input: String): Option[(Square, Square)] =
    val trimmed = input.trim.toLowerCase
    Option
      .when(trimmed.length == 4)(trimmed)
      .flatMap: s =>
        for
          from <- parseSquare(s.substring(0, 2))
          to   <- parseSquare(s.substring(2, 4))
        yield (from, to)

  private def parseSquare(s: String): Option[Square] =
    Option
      .when(s.length == 2)(s)
      .flatMap: sq =>
        val fileIdx = sq(0) - 'a'
        val rankIdx = sq(1) - '1'
        Option.when(fileIdx >= 0 && fileIdx <= 7 && rankIdx >= 0 && rankIdx <= 7)(
          Square(File.values(fileIdx), Rank.values(rankIdx)),
        )
