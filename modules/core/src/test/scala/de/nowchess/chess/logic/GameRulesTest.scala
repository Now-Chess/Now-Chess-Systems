package de.nowchess.chess.logic

import de.nowchess.api.board.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameRulesTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)
  private def board(entries: (Square, Piece)*): Board = Board(entries.toMap)

  // ──── isInCheck ──────────────────────────────────────────────────────

  test("isInCheck: king attacked by enemy rook on same rank"):
    // White King E1, Black Rook A1 — rook slides along rank 1 to E1
    val b = board(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.A, Rank.R1) -> Piece.BlackRook
    )
    GameRules.isInCheck(b, Color.White) shouldBe true

  test("isInCheck: king not attacked"):
    // Black Rook A3 does not cover E1
    val b = board(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.A, Rank.R3) -> Piece.BlackRook
    )
    GameRules.isInCheck(b, Color.White) shouldBe false

  test("isInCheck: no king on board returns false"):
    val b = board(sq(File.A, Rank.R1) -> Piece.BlackRook)
    GameRules.isInCheck(b, Color.White) shouldBe false

  // ──── legalMoves ─────────────────────────────────────────────────────

  test("legalMoves: move that exposes own king to rook is excluded"):
    // White King E1, White Rook E4 (pinned on E-file), Black Rook E8
    // Moving the White Rook off the E-file would expose the king
    val b = board(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.E, Rank.R4) -> Piece.WhiteRook,
      sq(File.E, Rank.R8) -> Piece.BlackRook
    )
    val moves = GameRules.legalMoves(b, Color.White)
    moves should not contain (sq(File.E, Rank.R4) -> sq(File.D, Rank.R4))

  test("legalMoves: move that blocks check is included"):
    // White King E1 in check from Black Rook E8; White Rook A5 can interpose on E5
    val b = board(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.A, Rank.R5) -> Piece.WhiteRook,
      sq(File.E, Rank.R8) -> Piece.BlackRook
    )
    val moves = GameRules.legalMoves(b, Color.White)
    moves should contain(sq(File.A, Rank.R5) -> sq(File.E, Rank.R5))

  // ──── gameStatus ──────────────────────────────────────────────────────

  test("gameStatus: checkmate returns Mated"):
    // White Qh8, Ka6; Black Ka8
    // Qh8 attacks Ka8 along rank 8; all escape squares covered (spec-verified position)
    val b = board(
      sq(File.H, Rank.R8) -> Piece.WhiteQueen,
      sq(File.A, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    )
    GameRules.gameStatus(b, Color.Black) shouldBe PositionStatus.Mated

  test("gameStatus: stalemate returns Drawn"):
    // White Qb6, Kc6; Black Ka8
    // Black king has no legal moves and is not in check (spec-verified position)
    val b = board(
      sq(File.B, Rank.R6) -> Piece.WhiteQueen,
      sq(File.C, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    )
    GameRules.gameStatus(b, Color.Black) shouldBe PositionStatus.Drawn

  test("gameStatus: king in check with legal escape returns InCheck"):
    // White Ra8 attacks Black Ke8 along rank 8; king can escape to d7, e7, f7
    val b = board(
      sq(File.A, Rank.R8) -> Piece.WhiteRook,
      sq(File.E, Rank.R8) -> Piece.BlackKing
    )
    GameRules.gameStatus(b, Color.Black) shouldBe PositionStatus.InCheck

  test("gameStatus: normal starting position returns Normal"):
    GameRules.gameStatus(Board.initial, Color.White) shouldBe PositionStatus.Normal
