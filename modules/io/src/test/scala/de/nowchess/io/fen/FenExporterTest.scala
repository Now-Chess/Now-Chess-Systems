package de.nowchess.io.fen

import de.nowchess.api.board.*
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FenExporterTest extends AnyFunSuite with Matchers:

  private def context(
    piecePlacement: String,
    turn: Color,
    castlingRights: CastlingRights,
    enPassantSquare: Option[Square],
    halfMoveClock: Int,
    moveCount: Int
  ): GameContext =
    val board = FenParser.parseBoard(piecePlacement).getOrElse(
      fail(s"Invalid test board FEN: $piecePlacement")
    )
    val dummyMove = Move(Square(File.A, Rank.R2), Square(File.A, Rank.R3))
    GameContext(
      board = board,
      turn = turn,
      castlingRights = castlingRights,
      enPassantSquare = enPassantSquare,
      halfMoveClock = halfMoveClock,
      moves = List.fill(moveCount)(dummyMove)
    )

  test("exportGameContextToFen handles initial and typical developed position"):
    FenExporter.gameContextToFen(GameContext.initial) shouldBe
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    val gameContext = context(
      piecePlacement = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR",
      turn = Color.Black,
      castlingRights = CastlingRights.All,
      enPassantSquare = Some(Square(File.E, Rank.R3)),
      halfMoveClock = 0,
      moveCount = 0
    )
    FenExporter.gameContextToFen(gameContext) shouldBe
      "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"

  test("export handles castling rights variants and en-passant with counters"):
    val noCastling = context(
      piecePlacement = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
      turn = Color.White,
      castlingRights = CastlingRights.None,
      enPassantSquare = None,
      halfMoveClock = 0,
      moveCount = 0
    )
    FenExporter.gameContextToFen(noCastling) shouldBe
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1"

    val partialCastling = context(
      piecePlacement = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
      turn = Color.White,
      castlingRights = CastlingRights(
        whiteKingSide = true,
        whiteQueenSide = false,
        blackKingSide = false,
        blackQueenSide = true
      ),
      enPassantSquare = None,
      halfMoveClock = 5,
      moveCount = 4
    )
    FenExporter.gameContextToFen(partialCastling) shouldBe
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w Kq - 5 3"

    val withEnPassant = context(
      piecePlacement = "rnbqkbnr/pp1ppppp/8/2pP4/8/8/PPPP1PPP/RNBQKBNR",
      turn = Color.White,
      castlingRights = CastlingRights.All,
      enPassantSquare = Some(Square(File.C, Rank.R6)),
      halfMoveClock = 2,
      moveCount = 4
    )
    FenExporter.gameContextToFen(withEnPassant) shouldBe
      "rnbqkbnr/pp1ppppp/8/2pP4/8/8/PPPP1PPP/RNBQKBNR w KQkq c6 2 3"

  test("halfMoveClock round-trips through FEN export and import"):
    val gameContext = GameContext(
      board = Board.initial,
      turn = Color.White,
      castlingRights = CastlingRights.All,
      enPassantSquare = None,
      halfMoveClock = 42,
      moves = List.empty
    )
    val fen = FenExporter.gameContextToFen(gameContext)
    FenParser.parseFen(fen) match
      case Right(ctx) => ctx.halfMoveClock shouldBe 42
      case Left(err)  => fail(s"FEN parsing failed: $err")

  test("exportGameContext forwards to gameContextToFen"):
    val ctx = GameContext.initial

    FenExporter.exportGameContext(ctx) shouldBe FenExporter.gameContextToFen(ctx)

