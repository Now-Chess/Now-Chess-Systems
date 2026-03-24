package de.nowchess.chess.logic

import de.nowchess.api.board.{Board, Color, File, Piece, Rank, Square}
import de.nowchess.api.game.CastlingRights
import de.nowchess.chess.logic.{GameContext, CastleSide}
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

  // ──── castlingTargets ────────────────────────────────────────────────

  private def ctxWithRights(
    entries: (Square, Piece)*
  )(white: CastlingRights = CastlingRights.Both,
    black: CastlingRights = CastlingRights.Both
  ): GameContext =
    GameContext(Board(entries.toMap), white, black)

  test("castlingTargets: white kingside available when all conditions met"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.H, Rank.R1) -> Piece.WhiteRook,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    )()
    MoveValidator.castlingTargets(ctx, Color.White) should contain(sq(File.G, Rank.R1))

  test("castlingTargets: white queenside available when all conditions met"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    )()
    MoveValidator.castlingTargets(ctx, Color.White) should contain(sq(File.C, Rank.R1))

  test("castlingTargets: black kingside available when all conditions met"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R8) -> Piece.BlackKing,
      sq(File.H, Rank.R8) -> Piece.BlackRook,
      sq(File.H, Rank.R1) -> Piece.WhiteKing
    )()
    MoveValidator.castlingTargets(ctx, Color.Black) should contain(sq(File.G, Rank.R8))

  test("castlingTargets: black queenside available when all conditions met"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R8) -> Piece.BlackKing,
      sq(File.A, Rank.R8) -> Piece.BlackRook,
      sq(File.H, Rank.R1) -> Piece.WhiteKing
    )()
    MoveValidator.castlingTargets(ctx, Color.Black) should contain(sq(File.C, Rank.R8))

  test("castlingTargets: blocked when transit square is occupied"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.F, Rank.R1) -> Piece.WhiteBishop,
      sq(File.H, Rank.R1) -> Piece.WhiteRook,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    )()
    MoveValidator.castlingTargets(ctx, Color.White) should not contain sq(File.G, Rank.R1)

  test("castlingTargets: blocked when king is in check"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.H, Rank.R1) -> Piece.WhiteRook,
      sq(File.E, Rank.R8) -> Piece.BlackRook,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    )()
    MoveValidator.castlingTargets(ctx, Color.White) shouldBe empty

  test("castlingTargets: blocked when transit square f1 is attacked"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.H, Rank.R1) -> Piece.WhiteRook,
      sq(File.F, Rank.R8) -> Piece.BlackRook,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    )()
    MoveValidator.castlingTargets(ctx, Color.White) should not contain sq(File.G, Rank.R1)

  test("castlingTargets: blocked when landing square g1 is attacked"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.H, Rank.R1) -> Piece.WhiteRook,
      sq(File.G, Rank.R8) -> Piece.BlackRook,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    )()
    MoveValidator.castlingTargets(ctx, Color.White) should not contain sq(File.G, Rank.R1)

  test("castlingTargets: blocked when kingSide right is false"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.H, Rank.R1) -> Piece.WhiteRook,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    )(white = CastlingRights(kingSide = false, queenSide = true))
    MoveValidator.castlingTargets(ctx, Color.White) should not contain sq(File.G, Rank.R1)

  test("castlingTargets: blocked when queenSide right is false"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    )(white = CastlingRights(kingSide = true, queenSide = false))
    MoveValidator.castlingTargets(ctx, Color.White) should not contain sq(File.C, Rank.R1)

  test("castlingTargets: blocked when relevant rook is not on home square"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.G, Rank.R1) -> Piece.WhiteRook,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    )()
    MoveValidator.castlingTargets(ctx, Color.White) should not contain sq(File.G, Rank.R1)

  // ──── context-aware legalTargets includes castling ────────────────────

  test("legalTargets(ctx, from): king on e1 includes g1 when castling available"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.H, Rank.R1) -> Piece.WhiteRook,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    )()
    MoveValidator.legalTargets(ctx, sq(File.E, Rank.R1)) should contain(sq(File.G, Rank.R1))

  test("legalTargets(ctx, from): non-king pieces unchanged by context"):
    val ctx = ctxWithRights(
      sq(File.D, Rank.R4) -> Piece.WhiteBishop,
      sq(File.H, Rank.R8) -> Piece.BlackKing,
      sq(File.H, Rank.R1) -> Piece.WhiteKing
    )()
    MoveValidator.legalTargets(ctx, sq(File.D, Rank.R4)) shouldBe
      MoveValidator.legalTargets(ctx.board, sq(File.D, Rank.R4))

  // ──── isCastle / castleSide / isLegal(ctx) ───────────────────────────

  test("isCastle: returns true when king moves two files"):
    val board = Board(Map(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.H, Rank.R1) -> Piece.WhiteRook
    ))
    MoveValidator.isCastle(board, sq(File.E, Rank.R1), sq(File.G, Rank.R1)) shouldBe true

  test("isCastle: returns false when king moves one file"):
    val board = Board(Map(
      sq(File.E, Rank.R1) -> Piece.WhiteKing
    ))
    MoveValidator.isCastle(board, sq(File.E, Rank.R1), sq(File.F, Rank.R1)) shouldBe false

  test("castleSide: returns Kingside when moving to higher file"):
    MoveValidator.castleSide(sq(File.E, Rank.R1), sq(File.G, Rank.R1)) shouldBe CastleSide.Kingside

  test("castleSide: returns Queenside when moving to lower file"):
    MoveValidator.castleSide(sq(File.E, Rank.R1), sq(File.C, Rank.R1)) shouldBe CastleSide.Queenside

  test("isLegal(ctx): returns true for legal castling move"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.H, Rank.R1) -> Piece.WhiteRook,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    )()
    MoveValidator.isLegal(ctx, sq(File.E, Rank.R1), sq(File.G, Rank.R1)) shouldBe true

  test("isLegal(ctx): returns false for illegal castling move when rights revoked"):
    val ctx = ctxWithRights(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.H, Rank.R1) -> Piece.WhiteRook,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    )(white = CastlingRights.None)
    MoveValidator.isLegal(ctx, sq(File.E, Rank.R1), sq(File.G, Rank.R1)) shouldBe false

  test("castlingTargets: returns empty when king not on home square"):
    val ctx = ctxWithRights(
      sq(File.D, Rank.R1) -> Piece.WhiteKing,
      sq(File.H, Rank.R1) -> Piece.WhiteRook,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    )()
    MoveValidator.castlingTargets(ctx, Color.White) shouldBe empty
