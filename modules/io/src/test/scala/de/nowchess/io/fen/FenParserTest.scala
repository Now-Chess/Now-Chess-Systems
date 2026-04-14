package de.nowchess.io.fen

import de.nowchess.api.board.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FenParserTest extends AnyFunSuite with Matchers:

  test("parseBoard parses canonical positions and supports round-trip"):
    val initial = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
    val empty   = "8/8/8/8/8/8/8/8"
    val partial = "8/8/4k3/8/4K3/8/8/8"

    FenParser.parseBoard(initial).map(_.pieceAt(Square(File.E, Rank.R2))) shouldBe Some(Some(Piece.WhitePawn))
    FenParser.parseBoard(initial).map(_.pieceAt(Square(File.E, Rank.R8))) shouldBe Some(Some(Piece.BlackKing))
    FenParser.parseBoard(empty).map(_.pieces.size) shouldBe Some(0)
    FenParser.parseBoard(partial).map(_.pieceAt(Square(File.E, Rank.R6))) shouldBe Some(Some(Piece.BlackKing))

    FenParser.parseBoard(initial).map(FenExporter.boardToFen) shouldBe Some(initial)
    FenParser.parseBoard(empty).map(FenExporter.boardToFen) shouldBe Some(empty)

  test("parseFen parses full state for common valid inputs"):
    FenParser
      .parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      .fold(
        _ => fail(),
        ctx =>
          ctx.turn shouldBe Color.White
          ctx.castlingRights.whiteKingSide shouldBe true
          ctx.enPassantSquare shouldBe None
          ctx.halfMoveClock shouldBe 0,
      )

    FenParser
      .parseFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
      .fold(
        _ => fail(),
        ctx =>
          ctx.turn shouldBe Color.Black
          ctx.enPassantSquare shouldBe Some(Square(File.E, Rank.R3)),
      )

    FenParser
      .parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1")
      .fold(
        _ => fail(),
        ctx =>
          ctx.castlingRights.whiteKingSide shouldBe false
          ctx.castlingRights.blackQueenSide shouldBe false,
      )

  test("parseFen rejects invalid color and castling tokens"):
    FenParser.parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1").isLeft shouldBe true
    FenParser.parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w XYZ - 0 1").isLeft shouldBe true

  test("importGameContext returns Right for valid and Left for invalid FEN"):
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    FenParser.importGameContext(fen).isRight shouldBe true
    FenParser.importGameContext("invalid fen string").isLeft shouldBe true

  test("parseBoard rejects malformed board shapes and invalid piece symbols"):
    FenParser.parseBoard("8/8/8/8/8/8/8") shouldBe None
    FenParser.parseBoard("9/8/8/8/8/8/8/8") shouldBe None
    FenParser.parseBoard("8p/8/8/8/8/8/8/8") shouldBe None
    FenParser.parseBoard("7/8/8/8/8/8/8/8") shouldBe None
    FenParser.parseBoard("8/8/8/8/8/8/8/7X") shouldBe None

  test("parseBoard rejects rank strings with invalid character followed by more characters"):
    FenParser.parseBoard("3X3p/8/8/8/8/8/8/8") shouldBe None

  test("parseFen rejects invalid move counts"):
    FenParser.parseFen("8/8/8/8/8/8/8/8 w - - -1 1").isLeft shouldBe true
    FenParser.parseFen("8/8/8/8/8/8/8/8 w - - 0 0").isLeft shouldBe true
