package de.nowchess.chess.logic

import de.nowchess.api.board.*
import de.nowchess.api.game.CastlingRights
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CastlingRightsCalculatorTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)

  test("Empty history gives full castling rights"):
    val rights = CastlingRightsCalculator.deriveCastlingRights(GameHistory.empty, Color.White)
    rights shouldBe CastlingRights.Both

  test("White loses kingside rights after h1 rook moves"):
    val history = GameHistory.empty.addMove(sq(File.H, Rank.R1), sq(File.H, Rank.R2))
    val rights = CastlingRightsCalculator.deriveCastlingRights(history, Color.White)
    rights.kingSide shouldBe false
    rights.queenSide shouldBe true

  test("White loses queenside rights after a1 rook moves"):
    val history = GameHistory.empty.addMove(sq(File.A, Rank.R1), sq(File.A, Rank.R2))
    val rights = CastlingRightsCalculator.deriveCastlingRights(history, Color.White)
    rights.queenSide shouldBe false
    rights.kingSide shouldBe true

  test("White loses all rights after king moves"):
    val history = GameHistory.empty.addMove(sq(File.E, Rank.R1), sq(File.E, Rank.R2))
    val rights = CastlingRightsCalculator.deriveCastlingRights(history, Color.White)
    rights shouldBe CastlingRights.None

  test("Black loses kingside rights after h8 rook moves"):
    val history = GameHistory.empty.addMove(sq(File.H, Rank.R8), sq(File.H, Rank.R7))
    val rights = CastlingRightsCalculator.deriveCastlingRights(history, Color.Black)
    rights.kingSide shouldBe false
    rights.queenSide shouldBe true

  test("Black loses queenside rights after a8 rook moves"):
    val history = GameHistory.empty.addMove(sq(File.A, Rank.R8), sq(File.A, Rank.R7))
    val rights = CastlingRightsCalculator.deriveCastlingRights(history, Color.Black)
    rights.queenSide shouldBe false
    rights.kingSide shouldBe true

  test("Black loses all rights after king moves"):
    val history = GameHistory.empty.addMove(sq(File.E, Rank.R8), sq(File.E, Rank.R7))
    val rights = CastlingRightsCalculator.deriveCastlingRights(history, Color.Black)
    rights shouldBe CastlingRights.None

  test("Castle move revokes all castling rights"):
    val history = GameHistory.empty.addMove(
      sq(File.E, Rank.R1),
      sq(File.G, Rank.R1),
      Some(CastleSide.Kingside)
    )
    val rights = CastlingRightsCalculator.deriveCastlingRights(history, Color.White)
    rights shouldBe CastlingRights.None

  test("Other pieces moving does not revoke castling rights"):
    val history = GameHistory.empty.addMove(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    val rights = CastlingRightsCalculator.deriveCastlingRights(history, Color.White)
    rights shouldBe CastlingRights.Both

  test("Multiple moves preserve white kingside but lose queenside"):
    val history = GameHistory.empty
      .addMove(sq(File.A, Rank.R1), sq(File.A, Rank.R2))  // White queenside rook moves
      .addMove(sq(File.E, Rank.R7), sq(File.E, Rank.R5))  // Black pawn moves
    val rights = CastlingRightsCalculator.deriveCastlingRights(history, Color.White)
    rights.kingSide shouldBe true
    rights.queenSide shouldBe false
