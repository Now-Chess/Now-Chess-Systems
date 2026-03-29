package de.nowchess.chess.logic

import de.nowchess.api.board.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class EnPassantCalculatorTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)
  private def board(entries: (Square, Piece)*): Board = Board(entries.toMap)

  // ──── enPassantTarget ────────────────────────────────────────────────

  test("enPassantTarget returns None for empty history"):
    val b = board(sq(File.E, Rank.R4) -> Piece.WhitePawn)
    EnPassantCalculator.enPassantTarget(b, GameHistory.empty) shouldBe None

  test("enPassantTarget returns None when last move was a single pawn push"):
    val b = board(sq(File.E, Rank.R3) -> Piece.WhitePawn)
    val h = GameHistory.empty.addMove(sq(File.E, Rank.R2), sq(File.E, Rank.R3))
    EnPassantCalculator.enPassantTarget(b, h) shouldBe None

  test("enPassantTarget returns None when last move was not a pawn"):
    val b = board(sq(File.E, Rank.R4) -> Piece.WhiteRook)
    val h = GameHistory.empty.addMove(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    EnPassantCalculator.enPassantTarget(b, h) shouldBe None

  test("enPassantTarget returns e3 after white pawn double push e2-e4"):
    val b = board(sq(File.E, Rank.R4) -> Piece.WhitePawn)
    val h = GameHistory.empty.addMove(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    EnPassantCalculator.enPassantTarget(b, h) shouldBe Some(sq(File.E, Rank.R3))

  test("enPassantTarget returns e6 after black pawn double push e7-e5"):
    val b = board(sq(File.E, Rank.R5) -> Piece.BlackPawn)
    val h = GameHistory.empty.addMove(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
    EnPassantCalculator.enPassantTarget(b, h) shouldBe Some(sq(File.E, Rank.R6))

  test("enPassantTarget returns d3 after white pawn double push d2-d4"):
    val b = board(sq(File.D, Rank.R4) -> Piece.WhitePawn)
    val h = GameHistory.empty.addMove(sq(File.D, Rank.R2), sq(File.D, Rank.R4))
    EnPassantCalculator.enPassantTarget(b, h) shouldBe Some(sq(File.D, Rank.R3))

  // ──── capturedPawnSquare ─────────────────────────────────────────────

  test("capturedPawnSquare for white capturing on e6 returns e5"):
    EnPassantCalculator.capturedPawnSquare(sq(File.E, Rank.R6), Color.White) shouldBe sq(File.E, Rank.R5)

  test("capturedPawnSquare for black capturing on e3 returns e4"):
    EnPassantCalculator.capturedPawnSquare(sq(File.E, Rank.R3), Color.Black) shouldBe sq(File.E, Rank.R4)

  test("capturedPawnSquare for white capturing on d6 returns d5"):
    EnPassantCalculator.capturedPawnSquare(sq(File.D, Rank.R6), Color.White) shouldBe sq(File.D, Rank.R5)

  // ──── isEnPassant ────────────────────────────────────────────────────

  test("isEnPassant returns true for valid white en passant capture"):
    // White pawn on e5, black pawn just double-pushed to d5 (ep target = d6)
    val b = board(
      sq(File.E, Rank.R5) -> Piece.WhitePawn,
      sq(File.D, Rank.R5) -> Piece.BlackPawn
    )
    val h = GameHistory.empty.addMove(sq(File.D, Rank.R7), sq(File.D, Rank.R5))
    EnPassantCalculator.isEnPassant(b, h, sq(File.E, Rank.R5), sq(File.D, Rank.R6)) shouldBe true

  test("isEnPassant returns true for valid black en passant capture"):
    // Black pawn on d4, white pawn just double-pushed to e4 (ep target = e3)
    val b = board(
      sq(File.D, Rank.R4) -> Piece.BlackPawn,
      sq(File.E, Rank.R4) -> Piece.WhitePawn
    )
    val h = GameHistory.empty.addMove(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    EnPassantCalculator.isEnPassant(b, h, sq(File.D, Rank.R4), sq(File.E, Rank.R3)) shouldBe true

  test("isEnPassant returns false when no en passant target in history"):
    val b = board(
      sq(File.E, Rank.R5) -> Piece.WhitePawn,
      sq(File.D, Rank.R5) -> Piece.BlackPawn
    )
    val h = GameHistory.empty.addMove(sq(File.D, Rank.R6), sq(File.D, Rank.R5))  // single push
    EnPassantCalculator.isEnPassant(b, h, sq(File.E, Rank.R5), sq(File.D, Rank.R6)) shouldBe false

  test("isEnPassant returns false when piece at from is not a pawn"):
    val b = board(
      sq(File.E, Rank.R5) -> Piece.WhiteRook,
      sq(File.D, Rank.R5) -> Piece.BlackPawn
    )
    val h = GameHistory.empty.addMove(sq(File.D, Rank.R7), sq(File.D, Rank.R5))
    EnPassantCalculator.isEnPassant(b, h, sq(File.E, Rank.R5), sq(File.D, Rank.R6)) shouldBe false

  test("isEnPassant returns false when to does not match ep target"):
    val b = board(
      sq(File.E, Rank.R5) -> Piece.WhitePawn,
      sq(File.D, Rank.R5) -> Piece.BlackPawn
    )
    val h = GameHistory.empty.addMove(sq(File.D, Rank.R7), sq(File.D, Rank.R5))
    EnPassantCalculator.isEnPassant(b, h, sq(File.E, Rank.R5), sq(File.E, Rank.R6)) shouldBe false

  test("isEnPassant returns false when from square is empty"):
    val b = board(sq(File.D, Rank.R5) -> Piece.BlackPawn)
    val h = GameHistory.empty.addMove(sq(File.D, Rank.R7), sq(File.D, Rank.R5))
    EnPassantCalculator.isEnPassant(b, h, sq(File.E, Rank.R5), sq(File.D, Rank.R6)) shouldBe false
