package de.nowchess.chess.logic

import de.nowchess.api.board.*
import de.nowchess.api.game.CastlingRights
import de.nowchess.chess.logic.GameHistory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameRulesTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)
  private def board(entries: (Square, Piece)*): Board = Board(entries.toMap)

  /** Wrap a board in a GameContext with no castling rights — for non-castling tests. */
  private def testLegalMoves(entries: (Square, Piece)*)(color: Color): Set[(Square, Square)] =
    GameRules.legalMoves(Board(entries.toMap), GameHistory.empty, color)

  private def testGameStatus(entries: (Square, Piece)*)(color: Color): PositionStatus =
    GameRules.gameStatus(Board(entries.toMap), GameHistory.empty, color)

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
    val moves = testLegalMoves(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.E, Rank.R4) -> Piece.WhiteRook,
      sq(File.E, Rank.R8) -> Piece.BlackRook
    )(Color.White)
    moves should not contain (sq(File.E, Rank.R4) -> sq(File.D, Rank.R4))

  test("legalMoves: move that blocks check is included"):
    // White King E1 in check from Black Rook E8; White Rook A5 can interpose on E5
    val moves = testLegalMoves(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.A, Rank.R5) -> Piece.WhiteRook,
      sq(File.E, Rank.R8) -> Piece.BlackRook
    )(Color.White)
    moves should contain(sq(File.A, Rank.R5) -> sq(File.E, Rank.R5))

  // ──── gameStatus ──────────────────────────────────────────────────────

  test("gameStatus: checkmate returns Mated"):
    // White Qh8, Ka6; Black Ka8
    // Qh8 attacks Ka8 along rank 8; all escape squares covered (spec-verified position)
    testGameStatus(
      sq(File.H, Rank.R8) -> Piece.WhiteQueen,
      sq(File.A, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    )(Color.Black) shouldBe PositionStatus.Mated

  test("gameStatus: stalemate returns Drawn"):
    // White Qb6, Kc6; Black Ka8
    // Black king has no legal moves and is not in check (spec-verified position)
    testGameStatus(
      sq(File.B, Rank.R6) -> Piece.WhiteQueen,
      sq(File.C, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    )(Color.Black) shouldBe PositionStatus.Drawn

  test("gameStatus: king in check with legal escape returns InCheck"):
    // White Ra8 attacks Black Ke8 along rank 8; king can escape to d7, e7, f7
    testGameStatus(
      sq(File.A, Rank.R8) -> Piece.WhiteRook,
      sq(File.E, Rank.R8) -> Piece.BlackKing
    )(Color.Black) shouldBe PositionStatus.InCheck

  test("gameStatus: normal starting position returns Normal"):
    GameRules.gameStatus(Board.initial, GameHistory.empty, Color.White) shouldBe PositionStatus.Normal

  test("legalMoves: includes castling destination when available"):
    val b = board(
        sq(File.E, Rank.R1) -> Piece.WhiteKing,
        sq(File.H, Rank.R1) -> Piece.WhiteRook,
        sq(File.H, Rank.R8) -> Piece.BlackKing
      )
    GameRules.legalMoves(b, GameHistory.empty, Color.White) should contain(sq(File.E, Rank.R1) -> sq(File.G, Rank.R1))

  test("legalMoves: excludes castling when king is in check"):
    val b = board(
        sq(File.E, Rank.R1) -> Piece.WhiteKing,
        sq(File.H, Rank.R1) -> Piece.WhiteRook,
        sq(File.E, Rank.R8) -> Piece.BlackRook,
        sq(File.A, Rank.R8) -> Piece.BlackKing
      )
    GameRules.legalMoves(b, GameHistory.empty, Color.White) should not contain (sq(File.E, Rank.R1) -> sq(File.G, Rank.R1))

  test("gameStatus: returns Normal (not Drawn) when castling is the only legal move"):
    // White King e1, Rook h1 (kingside castling available).
    // Black Rooks d2 and f2 box the king: d1 attacked by d2, e2 attacked by both,
    // f1 attacked by f2. King cannot move to any adjacent square without entering
    // an attacked square or an enemy piece. Only legal move: castle to g1.
    val b = board(
        sq(File.E, Rank.R1) -> Piece.WhiteKing,
        sq(File.H, Rank.R1) -> Piece.WhiteRook,
        sq(File.D, Rank.R2) -> Piece.BlackRook,
        sq(File.F, Rank.R2) -> Piece.BlackRook,
        sq(File.A, Rank.R8) -> Piece.BlackKing
      )
    // No history means castling rights are intact
    GameRules.gameStatus(b, GameHistory.empty, Color.White) shouldBe PositionStatus.Normal

  test("CastleSide.withCastle correctly positions pieces for Queenside castling"):
    // Directly test the withCastle extension for Queenside (coverage gap on line 10)
    val b = board(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    )
    val result = b.withCastle(Color.White, CastleSide.Queenside)
    result.pieceAt(sq(File.C, Rank.R1)) shouldBe Some(Piece.WhiteKing)
    result.pieceAt(sq(File.D, Rank.R1)) shouldBe Some(Piece.WhiteRook)
    result.pieceAt(sq(File.E, Rank.R1)) shouldBe None
    result.pieceAt(sq(File.A, Rank.R1)) shouldBe None

  test("CastleSide.withCastle correctly positions pieces for Black Kingside castling"):
    val b = board(
      sq(File.E, Rank.R8) -> Piece.BlackKing,
      sq(File.H, Rank.R8) -> Piece.BlackRook,
      sq(File.A, Rank.R1) -> Piece.WhiteKing
    )
    val result = b.withCastle(Color.Black, CastleSide.Kingside)
    result.pieceAt(sq(File.G, Rank.R8)) shouldBe Some(Piece.BlackKing)
    result.pieceAt(sq(File.F, Rank.R8)) shouldBe Some(Piece.BlackRook)
    result.pieceAt(sq(File.E, Rank.R8)) shouldBe None
    result.pieceAt(sq(File.H, Rank.R8)) shouldBe None

  test("CastleSide.withCastle correctly positions pieces for Black Queenside castling"):
    val b = board(
      sq(File.E, Rank.R8) -> Piece.BlackKing,
      sq(File.A, Rank.R8) -> Piece.BlackRook,
      sq(File.A, Rank.R1) -> Piece.WhiteKing
    )
    val result = b.withCastle(Color.Black, CastleSide.Queenside)
    result.pieceAt(sq(File.C, Rank.R8)) shouldBe Some(Piece.BlackKing)
    result.pieceAt(sq(File.D, Rank.R8)) shouldBe Some(Piece.BlackRook)
    result.pieceAt(sq(File.E, Rank.R8)) shouldBe None
    result.pieceAt(sq(File.A, Rank.R8)) shouldBe None
