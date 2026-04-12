package de.nowchess.io.pgn

import de.nowchess.api.board.*
import de.nowchess.api.move.{MoveType, PromotionPiece}
import de.nowchess.api.game.GameContext
import de.nowchess.io.fen.FenParser
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PgnParserTest extends AnyFunSuite with Matchers:

  test("parsePgn handles headers standard sequences captures castling and skipped tokens"):
    val headerOnly  = """[Event "Test Game"]
[White "Alice"]
[Black "Bob"]
[Result "1-0"]"""
    val onlyHeaders = PgnParser.parsePgn(headerOnly)
    onlyHeaders.isDefined shouldBe true
    onlyHeaders.get.headers("Event") shouldBe "Test Game"
    onlyHeaders.get.headers("White") shouldBe "Alice"

    val simple = PgnParser.parsePgn("""[Event "Test"]

1. e4 e5 2. Nf3 Nc6""")
    simple.map(_.moves.length) shouldBe Some(4)

    val capture = PgnParser.parsePgn("""[Event "Test"]

1. Nf3 e5 2. Nxe5""")
    capture.map(_.moves.length) shouldBe Some(3)
    capture.get.moves(2).to shouldBe Square(File.E, Rank.R5)

    val whiteKs = PgnParser
      .parsePgn("""[Event "Test"]

1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. O-O""")
      .get
      .moves
      .last
    whiteKs.moveType shouldBe MoveType.CastleKingside
    whiteKs.from shouldBe Square(File.E, Rank.R1)
    whiteKs.to shouldBe Square(File.G, Rank.R1)

    val whiteQs = PgnParser
      .parsePgn("""[Event "Test"]

1. d4 d5 2. Nc3 Nc6 3. Bf4 Bf5 4. Qd2 Qd7 5. O-O-O""")
      .get
      .moves
      .last
    whiteQs.moveType shouldBe MoveType.CastleQueenside
    whiteQs.from shouldBe Square(File.E, Rank.R1)
    whiteQs.to shouldBe Square(File.C, Rank.R1)

    val blackKs = PgnParser
      .parsePgn("""[Event "Test"]

1. e4 e5 2. Nf3 Nf6 3. Bc4 Be7 4. O-O O-O""")
      .get
      .moves
      .last
    blackKs.moveType shouldBe MoveType.CastleKingside
    blackKs.from shouldBe Square(File.E, Rank.R8)

    val blackQs = PgnParser
      .parsePgn("""[Event "Test"]

1. d4 d5 2. Nc3 Nc6 3. Bf4 Bf5 4. Qd2 Qd7 5. O-O-O O-O-O""")
      .get
      .moves
      .last
    blackQs.moveType shouldBe MoveType.CastleQueenside
    blackQs.from shouldBe Square(File.E, Rank.R8)
    blackQs.to shouldBe Square(File.C, Rank.R8)

    PgnParser
      .parsePgn("""[Event "Test"]

1. e4 e5 1-0""")
      .map(_.moves.length) shouldBe Some(2)
    PgnParser
      .parsePgn("""[Event "Test"]

1. e4 INVALID e5""")
      .map(_.moves.length) shouldBe Some(2)

  test("parseAlgebraicMove resolves pawn knight king and disambiguation cases"):
    val board = Board.initial
    PgnParser.parseAlgebraicMove("e4", GameContext.initial.withBoard(board), Color.White).get.to shouldBe Square(
      File.E,
      Rank.R4,
    )
    PgnParser.parseAlgebraicMove("Nf3", GameContext.initial.withBoard(board), Color.White).get.to shouldBe Square(
      File.F,
      Rank.R3,
    )

    val rookPieces: Map[Square, Piece] = Map(
      Square(File.A, Rank.R1) -> Piece(Color.White, PieceType.Rook),
      Square(File.H, Rank.R1) -> Piece(Color.White, PieceType.Rook),
      Square(File.E, Rank.R1) -> Piece(Color.White, PieceType.King),
      Square(File.E, Rank.R8) -> Piece(Color.Black, PieceType.King),
    )
    val rankPieces: Map[Square, Piece] = Map(
      Square(File.A, Rank.R1) -> Piece(Color.White, PieceType.Rook),
      Square(File.A, Rank.R4) -> Piece(Color.White, PieceType.Rook),
      Square(File.E, Rank.R1) -> Piece(Color.White, PieceType.King),
      Square(File.E, Rank.R8) -> Piece(Color.Black, PieceType.King),
    )
    PgnParser
      .parseAlgebraicMove("Rad1", GameContext.initial.withBoard(Board(rookPieces)), Color.White)
      .get
      .from shouldBe Square(File.A, Rank.R1)
    PgnParser
      .parseAlgebraicMove("R1a3", GameContext.initial.withBoard(Board(rankPieces)), Color.White)
      .get
      .from shouldBe Square(File.A, Rank.R1)

    val kingBoard = FenParser.parseBoard("4k3/8/8/8/8/8/8/4K3").get
    val king      = PgnParser.parseAlgebraicMove("Ke2", GameContext.initial.withBoard(kingBoard), Color.White)
    king.isDefined shouldBe true
    king.get.from shouldBe Square(File.E, Rank.R1)
    king.get.to shouldBe Square(File.E, Rank.R2)

  test("parseAlgebraicMove handles all promotion targets"):
    val board = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    PgnParser
      .parseAlgebraicMove("e7e8=Q", GameContext.initial.withBoard(board), Color.White)
      .get
      .moveType shouldBe MoveType.Promotion(PromotionPiece.Queen)
    PgnParser
      .parseAlgebraicMove("e7e8=R", GameContext.initial.withBoard(board), Color.White)
      .get
      .moveType shouldBe MoveType.Promotion(PromotionPiece.Rook)
    PgnParser
      .parseAlgebraicMove("e7e8=B", GameContext.initial.withBoard(board), Color.White)
      .get
      .moveType shouldBe MoveType.Promotion(PromotionPiece.Bishop)
    PgnParser
      .parseAlgebraicMove("e7e8=N", GameContext.initial.withBoard(board), Color.White)
      .get
      .moveType shouldBe MoveType.Promotion(PromotionPiece.Knight)

  test("importGameContext accepts valid and empty PGN"):
    val pgn = """[Event "Test"]

1. e4 e5"""
    PgnParser.importGameContext(pgn).isRight shouldBe true
    PgnParser.importGameContext("").isRight shouldBe true

  test("parser edge cases: uppercase token hint chars and promotion mismatch handling"):
    PgnParser.parseAlgebraicMove("E5", GameContext.initial, Color.White) shouldBe None
    PgnParser.parseAlgebraicMove("N?f3", GameContext.initial, Color.White).get.to shouldBe Square(File.F, Rank.R3)
    PgnParser.extractPromotion("e7e8=X") shouldBe None

    val board = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    PgnParser.parseAlgebraicMove("e8", GameContext.initial.withBoard(board), Color.White) shouldBe None

  test("parseAlgebraicMove rejects too-short notation and invalid piece letters"):
    val initial = GameContext.initial

    PgnParser.parseAlgebraicMove("e", initial, Color.White) shouldBe None
    PgnParser.parseAlgebraicMove("Xe5", initial, Color.White) shouldBe None

  test("parseAlgebraicMove rejects notation with invalid promotion piece"):
    val board   = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").getOrElse(fail("valid board expected"))
    val context = GameContext.initial.withBoard(board)

    PgnParser.parseAlgebraicMove("e7e8=X", context, Color.White) shouldBe None

  test("parsePgn silently skips unknown tokens"):
    val parsed = PgnParser.parsePgn("1. e4 ??? e5")

    parsed.map(_.moves.size) shouldBe Some(2)
