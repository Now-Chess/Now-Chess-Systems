package de.nowchess.chess.notation

import de.nowchess.api.board.*
import de.nowchess.api.game.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FenExporterTest extends AnyFunSuite with Matchers:

  test("export initial position to FEN"):
    val gameState = GameState.initial
    val fen = FenExporter.gameStateToFen(gameState)
    fen shouldBe "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  test("export position after e4"):
    val gameState = GameState(
      piecePlacement = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR",
      activeColor = Color.Black,
      castlingWhite = CastlingRights.Both,
      castlingBlack = CastlingRights.Both,
      enPassantTarget = Some(Square(File.E, Rank.R3)),
      halfMoveClock = 0,
      fullMoveNumber = 1,
      status = GameStatus.InProgress
    )
    val fen = FenExporter.gameStateToFen(gameState)
    fen shouldBe "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"

  test("export position with no castling"):
    val gameState = GameState(
      piecePlacement = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
      activeColor = Color.White,
      castlingWhite = CastlingRights.None,
      castlingBlack = CastlingRights.None,
      enPassantTarget = None,
      halfMoveClock = 0,
      fullMoveNumber = 1,
      status = GameStatus.InProgress
    )
    val fen = FenExporter.gameStateToFen(gameState)
    fen shouldBe "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1"

  test("export position with partial castling"):
    val gameState = GameState(
      piecePlacement = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
      activeColor = Color.White,
      castlingWhite = CastlingRights(kingSide = true, queenSide = false),
      castlingBlack = CastlingRights(kingSide = false, queenSide = true),
      enPassantTarget = None,
      halfMoveClock = 5,
      fullMoveNumber = 3,
      status = GameStatus.InProgress
    )
    val fen = FenExporter.gameStateToFen(gameState)
    fen shouldBe "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w Kq - 5 3"

  test("export position with en passant and move counts"):
    val gameState = GameState(
      piecePlacement = "rnbqkbnr/pp1ppppp/8/2pP4/8/8/PPPP1PPP/RNBQKBNR",
      activeColor = Color.White,
      castlingWhite = CastlingRights.Both,
      castlingBlack = CastlingRights.Both,
      enPassantTarget = Some(Square(File.C, Rank.R6)),
      halfMoveClock = 2,
      fullMoveNumber = 3,
      status = GameStatus.InProgress
    )
    val fen = FenExporter.gameStateToFen(gameState)
    fen shouldBe "rnbqkbnr/pp1ppppp/8/2pP4/8/8/PPPP1PPP/RNBQKBNR w KQkq c6 2 3"
