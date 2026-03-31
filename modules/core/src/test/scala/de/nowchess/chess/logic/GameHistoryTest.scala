package de.nowchess.chess.logic

import de.nowchess.api.board.*
import de.nowchess.api.move.PromotionPiece
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

  test("Move with promotion records the promotion piece"):
    val move = HistoryMove(sq(File.E, Rank.R7), sq(File.E, Rank.R8), None, Some(PromotionPiece.Queen))
    move.promotionPiece should be (Some(PromotionPiece.Queen))

  test("Normal move has no promotion piece"):
    val move = HistoryMove(sq(File.E, Rank.R2), sq(File.E, Rank.R4), None, None)
    move.promotionPiece should be (None)

  test("addMove with promotion stores promotionPiece"):
    val history = GameHistory.empty
    val newHistory = history.addMove(sq(File.E, Rank.R7), sq(File.E, Rank.R8), None, Some(PromotionPiece.Rook))
    newHistory.moves should have length 1
    newHistory.moves.head.promotionPiece should be (Some(PromotionPiece.Rook))

  test("addMove with castleSide only uses promotionPiece default (None)"):
    val history = GameHistory.empty
    // With overload 3 removed, this uses the 4-param version and triggers addMove$default$4
    val newHistory = history.addMove(sq(File.E, Rank.R1), sq(File.G, Rank.R1), Some(CastleSide.Kingside))
    newHistory.moves should have length 1
    newHistory.moves.head.castleSide should be (Some(CastleSide.Kingside))
    newHistory.moves.head.promotionPiece should be (None)

  test("addMove using named parameters with only promotion, using castleSide default"):
    val history = GameHistory.empty
    val newHistory = history.addMove(from = sq(File.E, Rank.R7), to = sq(File.E, Rank.R8), promotionPiece = Some(PromotionPiece.Queen))
    newHistory.moves should have length 1
    newHistory.moves.head.castleSide should be (None)
    newHistory.moves.head.promotionPiece should be (Some(PromotionPiece.Queen))
