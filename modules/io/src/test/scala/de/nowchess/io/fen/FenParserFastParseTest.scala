package de.nowchess.io.fen

import de.nowchess.api.board.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FenParserFastParseTest extends AnyFunSuite with Matchers:

  test("parseBoard parses canonical positions and supports round-trip"):
    val initial = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
    val empty   = "8/8/8/8/8/8/8/8"
    val partial = "8/8/4k3/8/4K3/8/8/8"

    FenParserFastParse.parseBoard(initial).map(_.pieceAt(Square(File.E, Rank.R2))) shouldBe Some(Some(Piece.WhitePawn))
    FenParserFastParse.parseBoard(initial).map(_.pieceAt(Square(File.E, Rank.R8))) shouldBe Some(Some(Piece.BlackKing))
    FenParserFastParse.parseBoard(empty).map(_.pieces.size) shouldBe Some(0)
    FenParserFastParse.parseBoard(partial).map(_.pieceAt(Square(File.E, Rank.R6))) shouldBe Some(Some(Piece.BlackKing))

    FenParserFastParse.parseBoard(initial).map(FenExporter.boardToFen) shouldBe Some(initial)
    FenParserFastParse.parseBoard(empty).map(FenExporter.boardToFen) shouldBe Some(empty)

  test("parseFen parses full state for common valid inputs"):
    FenParserFastParse
      .parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      .fold(
        _ => fail(),
        ctx =>
          ctx.turn shouldBe Color.White
          ctx.castlingRights.whiteKingSide shouldBe true
          ctx.enPassantSquare shouldBe None
          ctx.halfMoveClock shouldBe 0,
      )

    FenParserFastParse
      .parseFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
      .fold(
        _ => fail(),
        ctx =>
          ctx.turn shouldBe Color.Black
          ctx.enPassantSquare shouldBe Some(Square(File.E, Rank.R3)),
      )

    FenParserFastParse
      .parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1")
      .fold(
        _ => fail(),
        ctx =>
          ctx.castlingRights.whiteKingSide shouldBe false
          ctx.castlingRights.blackQueenSide shouldBe false,
      )

  test("parseFen rejects invalid color and castling tokens"):
    FenParserFastParse.parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1").isLeft shouldBe true
    FenParserFastParse.parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w XYZ - 0 1").isLeft shouldBe true

  test("importGameContext returns Right for valid and Left for invalid FEN"):
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    FenParserFastParse.importGameContext(fen).isRight shouldBe true
    FenParserFastParse.importGameContext("invalid fen string").isLeft shouldBe true

  test("parseBoard rejects malformed board shapes and invalid piece symbols"):
    FenParserFastParse.parseBoard("8/8/8/8/8/8/8") shouldBe None
    FenParserFastParse.parseBoard("9/8/8/8/8/8/8/8") shouldBe None
    FenParserFastParse.parseBoard("8p/8/8/8/8/8/8/8") shouldBe None
    FenParserFastParse.parseBoard("7/8/8/8/8/8/8/8") shouldBe None
    FenParserFastParse.parseBoard("8/8/8/8/8/8/8/7X") shouldBe None

  test("parseBoard rejects ranks that overflow via multiple tokens"):
    FenParserFastParse.parseBoard("p8/8/8/8/8/8/8/8") shouldBe None
    FenParserFastParse.parseBoard("8pp/8/8/8/8/8/8/8") shouldBe None

  test("parseFen handles all individual castling rights"):
    FenParserFastParse.parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w K - 0 1").fold(_ => fail(), ctx =>
      ctx.castlingRights.whiteKingSide shouldBe true
      ctx.castlingRights.whiteQueenSide shouldBe false
      ctx.castlingRights.blackKingSide shouldBe false
      ctx.castlingRights.blackQueenSide shouldBe false
    )

    FenParserFastParse.parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w Q - 0 1").fold(_ => fail(), ctx =>
      ctx.castlingRights.whiteQueenSide shouldBe true
      ctx.castlingRights.whiteKingSide shouldBe false
    )

    FenParserFastParse.parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w k - 0 1").fold(_ => fail(), ctx =>
      ctx.castlingRights.blackKingSide shouldBe true
      ctx.castlingRights.whiteKingSide shouldBe false
    )

    FenParserFastParse.parseFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w q - 0 1").fold(_ => fail(), ctx =>
      ctx.castlingRights.blackQueenSide shouldBe true
      ctx.castlingRights.whiteKingSide shouldBe false
    )

  test("parseFen parses all en passant squares"):
    FenParserFastParse.parseFen("8/8/8/8/8/8/8/8 w - a3 0 1").fold(_ => fail(), ctx =>
      ctx.enPassantSquare shouldBe Some(Square(File.A, Rank.R3))
    )

    FenParserFastParse.parseFen("8/8/8/8/8/8/8/8 w - h6 0 1").fold(_ => fail(), ctx =>
      ctx.enPassantSquare shouldBe Some(Square(File.H, Rank.R6))
    )

  test("parseFen parses different halfMove and fullMove clocks"):
    FenParserFastParse.parseFen("8/8/8/8/8/8/8/8 w - - 5 10").fold(_ => fail(), ctx =>
      ctx.halfMoveClock shouldBe 5
    )

    FenParserFastParse.parseFen("8/8/8/8/8/8/8/8 w - - 0 100").fold(_ => fail(), ctx =>
      ctx.halfMoveClock shouldBe 0
    )

  test("parseBoard parses boards with mixed empty and piece tokens"):
    val mixed = "8/1p1p1p1p/8/1P1P1P1P/8/8/8/8"
    FenParserFastParse.parseBoard(mixed) should not be empty

  test("parseFen handles turn transitions"):
    FenParserFastParse.parseFen("8/8/8/8/8/8/8/8 w - - 0 1").fold(_ => fail(), ctx =>
      ctx.turn shouldBe Color.White
    )

    FenParserFastParse.parseFen("8/8/8/8/8/8/8/8 b - - 0 1").fold(_ => fail(), ctx =>
      ctx.turn shouldBe Color.Black
    )

  test("parseFen rejects invalid piece characters"):
    FenParserFastParse.parseFen("8x/8/8/8/8/8/8/8 w - - 0 1").isLeft shouldBe true

  test("parseFen rejects incomplete FEN strings"):
    FenParserFastParse.parseFen("8/8/8/8/8/8/8/8 w - -").isLeft shouldBe true
    FenParserFastParse.parseFen("8/8/8/8/8/8/8/8 w").isLeft shouldBe true

  test("parseBoard tests all piece types in various positions"):
    // Test each piece type: pawn, rook, knight, bishop, queen, king (both colors)
    val allPieces = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
    val parsed = FenParserFastParse.parseBoard(allPieces)
    parsed.map(_.pieces.size) shouldBe Some(32)
    parsed.map(_.pieceAt(Square(File.A, Rank.R8))) shouldBe Some(Some(Piece.BlackRook))
    parsed.map(_.pieceAt(Square(File.B, Rank.R8))) shouldBe Some(Some(Piece.BlackKnight))
    parsed.map(_.pieceAt(Square(File.C, Rank.R8))) shouldBe Some(Some(Piece.BlackBishop))
    parsed.map(_.pieceAt(Square(File.D, Rank.R8))) shouldBe Some(Some(Piece.BlackQueen))
    parsed.map(_.pieceAt(Square(File.E, Rank.R8))) shouldBe Some(Some(Piece.BlackKing))

  test("parseBoard tests all empty counts from 1 to 8"):
    FenParserFastParse.parseBoard("1p6/2p5/3p4/4p3/5p2/6p1/7p/8") should not be empty
    FenParserFastParse.parseBoard("8/1p6/2p5/3p4/4p3/5p2/6p1/7p") should not be empty

  test("parseFen tests all valid colors"):
    FenParserFastParse.parseFen("8/8/8/8/8/8/8/8 w - - 0 1").fold(_ => fail(), _.turn shouldBe Color.White)
    FenParserFastParse.parseFen("8/8/8/8/8/8/8/8 b - - 0 1").fold(_ => fail(), _.turn shouldBe Color.Black)

  test("parseFen tests all castling combinations"):
    FenParserFastParse.parseFen("8/8/8/8/8/8/8/8 w KQkq - 0 1").fold(_ => fail(), ctx =>
      ctx.castlingRights.whiteKingSide shouldBe true
      ctx.castlingRights.whiteQueenSide shouldBe true
      ctx.castlingRights.blackKingSide shouldBe true
      ctx.castlingRights.blackQueenSide shouldBe true
    )

    FenParserFastParse.parseFen("8/8/8/8/8/8/8/8 w Kq - 0 1").fold(_ => fail(), ctx =>
      ctx.castlingRights.whiteKingSide shouldBe true
      ctx.castlingRights.whiteQueenSide shouldBe false
      ctx.castlingRights.blackKingSide shouldBe false
      ctx.castlingRights.blackQueenSide shouldBe true
    )

  test("parseFen tests all en passant files"):
    for file <- Seq("a", "b", "c", "d", "e", "f", "g", "h") do
      FenParserFastParse.parseFen(s"8/8/8/8/8/8/8/8 w - ${file}3 0 1").fold(_ => fail(), ctx =>
        ctx.enPassantSquare should not be empty
      )

  test("parseBoard with mixed pieces and empty squares"):
    FenParserFastParse.parseBoard("r1bqkb1r/pppppppp/2n2n2/8/8/2N2N2/PPPPPPPP/R1BQKB1R") should not be empty
