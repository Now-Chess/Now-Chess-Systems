package de.nowchess.chess.logic

import de.nowchess.api.board.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameHistoryTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)

  test("GameHistory starts empty"):
    val history = GameHistory.empty
    history.moves shouldBe empty

  test("GameHistory can add a move"):
    val history = GameHistory.empty.addMove(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    history.moves should have length 1
    history.moves.head.from shouldBe sq(File.E, Rank.R2)
    history.moves.head.to shouldBe sq(File.E, Rank.R4)
    history.moves.head.castleSide shouldBe None

  test("GameHistory can add multiple moves in order"):
    val h1 = GameHistory.empty
    val h2 = h1.addMove(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    val h3 = h2.addMove(sq(File.C, Rank.R7), sq(File.C, Rank.R5))
    h3.moves should have length 2
    h3.moves(0).from shouldBe sq(File.E, Rank.R2)
    h3.moves(1).from shouldBe sq(File.C, Rank.R7)

  test("GameHistory can add a castle move"):
    val history = GameHistory.empty.addMove(
      sq(File.E, Rank.R1),
      sq(File.G, Rank.R1),
      Some(CastleSide.Kingside)
    )
    history.moves.head.castleSide shouldBe Some(CastleSide.Kingside)

  test("GameHistory.addMove with two arguments uses None for castleSide default"):
    val history = GameHistory.empty.addMove(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    history.moves should have length 1
    history.moves.head.castleSide shouldBe None
