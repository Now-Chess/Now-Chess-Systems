package de.nowchess.chess.logic

import de.nowchess.api.board.{Board, Color, File, Piece, Rank, Square}
import de.nowchess.api.game.CastlingRights
import de.nowchess.chess.logic.{CastleSide, GameHistory}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MoveValidatorTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)
  private def board(entries: (Square, Piece)*): Board = Board(entries.toMap)

  // ──── Empty square ───────────────────────────────────────────────────

  test("legalTargets returns empty set when no piece at from square"):
    MoveValidator.legalTargets(Board.initial, sq(File.E, Rank.R4)) shouldBe empty

  // ──── isLegal delegates to legalTargets ──────────────────────────────

  test("isLegal returns true for a valid pawn move"):
    MoveValidator.isLegal(Board.initial, sq(File.E, Rank.R2), sq(File.E, Rank.R4)) shouldBe true

  test("isLegal returns false for an invalid move"):
    MoveValidator.isLegal(Board.initial, sq(File.E, Rank.R2), sq(File.E, Rank.R5)) shouldBe false

  // ──── Pawn – White ───────────────────────────────────────────────────

  test("white pawn on starting rank can move forward one square"):
    val b = board(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    MoveValidator.legalTargets(b, sq(File.E, Rank.R2)) should contain(sq(File.E, Rank.R3))

  test("white pawn on starting rank can move forward two squares"):
    val b = board(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    MoveValidator.legalTargets(b, sq(File.E, Rank.R2)) should contain(sq(File.E, Rank.R4))

  test("white pawn not on starting rank cannot move two squares"):
    val b = board(sq(File.E, Rank.R3) -> Piece.WhitePawn)
    MoveValidator.legalTargets(b, sq(File.E, Rank.R3)) should not contain sq(File.E, Rank.R5)

  test("white pawn is blocked by piece directly in front, and cannot jump over it"):
    val b = board(
      sq(File.E, Rank.R2) -> Piece.WhitePawn,
      sq(File.E, Rank.R3) -> Piece.BlackPawn
    )
    val targets = MoveValidator.legalTargets(b, sq(File.E, Rank.R2))
    targets should not contain sq(File.E, Rank.R3)
    targets should not contain sq(File.E, Rank.R4)

  test("white pawn on starting rank cannot move two squares if destination square is occupied"):
    val b = board(
      sq(File.E, Rank.R2) -> Piece.WhitePawn,
      sq(File.E, Rank.R4) -> Piece.BlackPawn
    )
    val targets = MoveValidator.legalTargets(b, sq(File.E, Rank.R2))
    targets should contain(sq(File.E, Rank.R3))
    targets should not contain sq(File.E, Rank.R4)

  test("white pawn can capture diagonally when enemy piece is present"):
    val b = board(
      sq(File.E, Rank.R2) -> Piece.WhitePawn,
      sq(File.D, Rank.R3) -> Piece.BlackPawn
    )
    MoveValidator.legalTargets(b, sq(File.E, Rank.R2)) should contain(sq(File.D, Rank.R3))

  test("white pawn cannot capture diagonally when no enemy piece is present"):
    val b = board(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    MoveValidator.legalTargets(b, sq(File.E, Rank.R2)) should not contain sq(File.D, Rank.R3)

  test("white pawn at A-file does not generate diagonal to the left off the board"):
    val b = board(sq(File.A, Rank.R2) -> Piece.WhitePawn)
    val targets = MoveValidator.legalTargets(b, sq(File.A, Rank.R2))
    targets should contain(sq(File.A, Rank.R3))
    targets should contain(sq(File.A, Rank.R4))
    targets.size shouldBe 2

  // ──── Pawn – Black ───────────────────────────────────────────────────

  test("black pawn on starting rank can move forward one and two squares"):
    val b = board(sq(File.E, Rank.R7) -> Piece.BlackPawn)
    val targets = MoveValidator.legalTargets(b, sq(File.E, Rank.R7))
    targets should contain(sq(File.E, Rank.R6))
    targets should contain(sq(File.E, Rank.R5))

  test("black pawn not on starting rank cannot move two squares"):
    val b = board(sq(File.E, Rank.R6) -> Piece.BlackPawn)
    MoveValidator.legalTargets(b, sq(File.E, Rank.R6)) should not contain sq(File.E, Rank.R4)

  test("black pawn can capture diagonally when enemy piece is present"):
    val b = board(
      sq(File.E, Rank.R7) -> Piece.BlackPawn,
      sq(File.F, Rank.R6) -> Piece.WhitePawn
    )
    MoveValidator.legalTargets(b, sq(File.E, Rank.R7)) should contain(sq(File.F, Rank.R6))

  // ──── Knight ─────────────────────────────────────────────────────────

  test("knight in center has 8 possible moves"):
    val b = board(sq(File.D, Rank.R4) -> Piece.WhiteKnight)
    MoveValidator.legalTargets(b, sq(File.D, Rank.R4)).size shouldBe 8

  test("knight in corner has only 2 possible moves"):
    val b = board(sq(File.A, Rank.R1) -> Piece.WhiteKnight)
    MoveValidator.legalTargets(b, sq(File.A, Rank.R1)).size shouldBe 2

  test("knight cannot land on own piece"):
    val b = board(
      sq(File.D, Rank.R4) -> Piece.WhiteKnight,
      sq(File.F, Rank.R5) -> Piece.WhiteRook
    )
    MoveValidator.legalTargets(b, sq(File.D, Rank.R4)) should not contain sq(File.F, Rank.R5)

  test("knight can capture enemy piece"):
    val b = board(
      sq(File.D, Rank.R4) -> Piece.WhiteKnight,
      sq(File.F, Rank.R5) -> Piece.BlackRook
    )
    MoveValidator.legalTargets(b, sq(File.D, Rank.R4)) should contain(sq(File.F, Rank.R5))

  // ──── Bishop ─────────────────────────────────────────────────────────

  test("bishop slides diagonally across an empty board"):
    val b = board(sq(File.D, Rank.R4) -> Piece.WhiteBishop)
    val targets = MoveValidator.legalTargets(b, sq(File.D, Rank.R4))
    targets should contain(sq(File.E, Rank.R5))
    targets should contain(sq(File.H, Rank.R8))
    targets should contain(sq(File.C, Rank.R3))
    targets should contain(sq(File.A, Rank.R1))

  test("bishop is blocked by own piece and squares beyond are unreachable"):
    val b = board(
      sq(File.D, Rank.R4) -> Piece.WhiteBishop,
      sq(File.F, Rank.R6) -> Piece.WhiteRook
    )
    val targets = MoveValidator.legalTargets(b, sq(File.D, Rank.R4))
    targets should contain(sq(File.E, Rank.R5))
    targets should not contain sq(File.F, Rank.R6)
    targets should not contain sq(File.G, Rank.R7)

  test("bishop captures enemy piece and cannot slide further"):
    val b = board(
      sq(File.D, Rank.R4) -> Piece.WhiteBishop,
      sq(File.F, Rank.R6) -> Piece.BlackRook
    )
    val targets = MoveValidator.legalTargets(b, sq(File.D, Rank.R4))
    targets should contain(sq(File.E, Rank.R5))
    targets should contain(sq(File.F, Rank.R6))
    targets should not contain sq(File.G, Rank.R7)

  // ──── Rook ───────────────────────────────────────────────────────────

  test("rook slides orthogonally across an empty board"):
    val b = board(sq(File.D, Rank.R4) -> Piece.WhiteRook)
    val targets = MoveValidator.legalTargets(b, sq(File.D, Rank.R4))
    targets should contain(sq(File.D, Rank.R8))
    targets should contain(sq(File.D, Rank.R1))
    targets should contain(sq(File.A, Rank.R4))
    targets should contain(sq(File.H, Rank.R4))

  test("rook is blocked by own piece and squares beyond are unreachable"):
    val b = board(
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.C, Rank.R1) -> Piece.WhitePawn
    )
    val targets = MoveValidator.legalTargets(b, sq(File.A, Rank.R1))
    targets should contain(sq(File.B, Rank.R1))
    targets should not contain sq(File.C, Rank.R1)
    targets should not contain sq(File.D, Rank.R1)

  test("rook captures enemy piece and cannot slide further"):
    val b = board(
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.C, Rank.R1) -> Piece.BlackPawn
    )
    val targets = MoveValidator.legalTargets(b, sq(File.A, Rank.R1))
    targets should contain(sq(File.B, Rank.R1))
    targets should contain(sq(File.C, Rank.R1))
    targets should not contain sq(File.D, Rank.R1)

  // ──── Queen ──────────────────────────────────────────────────────────

  test("queen combines rook and bishop movement for 27 squares from d4"):
    val b = board(sq(File.D, Rank.R4) -> Piece.WhiteQueen)
    val targets = MoveValidator.legalTargets(b, sq(File.D, Rank.R4))
    targets should contain(sq(File.D, Rank.R8))
    targets should contain(sq(File.H, Rank.R4))
    targets should contain(sq(File.H, Rank.R8))
    targets should contain(sq(File.A, Rank.R1))
    targets.size shouldBe 27

  // ──── King ───────────────────────────────────────────────────────────

  test("king moves one step in all 8 directions from center"):
    val b = board(sq(File.D, Rank.R4) -> Piece.WhiteKing)
    MoveValidator.legalTargets(b, sq(File.D, Rank.R4)).size shouldBe 8

  test("king at corner has only 3 reachable squares"):
    val b = board(sq(File.A, Rank.R1) -> Piece.WhiteKing)
    MoveValidator.legalTargets(b, sq(File.A, Rank.R1)).size shouldBe 3

  test("king cannot capture own piece"):
    val b = board(
      sq(File.D, Rank.R4) -> Piece.WhiteKing,
      sq(File.E, Rank.R4) -> Piece.WhiteRook
    )
    MoveValidator.legalTargets(b, sq(File.D, Rank.R4)) should not contain sq(File.E, Rank.R4)

  test("king can capture enemy piece"):
    val b = board(
      sq(File.D, Rank.R4) -> Piece.WhiteKing,
      sq(File.E, Rank.R4) -> Piece.BlackRook
    )
    MoveValidator.legalTargets(b, sq(File.D, Rank.R4)) should contain(sq(File.E, Rank.R4))

  // ──── Pawn – en passant targets ──────────────────────────────────────

  test("white pawn includes ep target in legal moves after black double push"):
    // Black pawn just double-pushed to d5 (ep target = d6); white pawn on e5
    val b = board(
      sq(File.E, Rank.R5) -> Piece.WhitePawn,
      sq(File.D, Rank.R5) -> Piece.BlackPawn
    )
    val h = GameHistory.empty.addMove(sq(File.D, Rank.R7), sq(File.D, Rank.R5))
    MoveValidator.legalTargets(b, h, sq(File.E, Rank.R5)) should contain(sq(File.D, Rank.R6))

  test("white pawn does not include ep target without a preceding double push"):
    val b = board(
      sq(File.E, Rank.R5) -> Piece.WhitePawn,
      sq(File.D, Rank.R5) -> Piece.BlackPawn
    )
    val h = GameHistory.empty.addMove(sq(File.D, Rank.R6), sq(File.D, Rank.R5))  // single push
    MoveValidator.legalTargets(b, h, sq(File.E, Rank.R5)) should not contain sq(File.D, Rank.R6)

  test("black pawn includes ep target in legal moves after white double push"):
    // White pawn just double-pushed to e4 (ep target = e3); black pawn on d4
    val b = board(
      sq(File.D, Rank.R4) -> Piece.BlackPawn,
      sq(File.E, Rank.R4) -> Piece.WhitePawn
    )
    val h = GameHistory.empty.addMove(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    MoveValidator.legalTargets(b, h, sq(File.D, Rank.R4)) should contain(sq(File.E, Rank.R3))

  test("pawn on wrong file does not get ep target from adjacent double push"):
    // White pawn on a5, black pawn double-pushed to d5 — a5 is not adjacent to d5
    val b = board(
      sq(File.A, Rank.R5) -> Piece.WhitePawn,
      sq(File.D, Rank.R5) -> Piece.BlackPawn
    )
    val h = GameHistory.empty.addMove(sq(File.D, Rank.R7), sq(File.D, Rank.R5))
    MoveValidator.legalTargets(b, h, sq(File.A, Rank.R5)) should not contain sq(File.D, Rank.R6)

  // ──── History-aware legalTargets fallback for non-pawn non-king pieces ─────

  test("legalTargets with history delegates to geometry-only for non-pawn non-king pieces"):
    val b = board(sq(File.D, Rank.R4) -> Piece.WhiteRook)
    val h = GameHistory.empty.addMove(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    MoveValidator.legalTargets(b, h, sq(File.D, Rank.R4)) shouldBe MoveValidator.legalTargets(b, sq(File.D, Rank.R4))
