package de.nowchess.chess.notation

import de.nowchess.api.board.*
import de.nowchess.api.game.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FenParserTest extends AnyFunSuite with Matchers:

  test("parseBoard: initial position places pieces on correct squares"):
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
    val board = FenParser.parseBoard(fen)

    board.map(_.pieceAt(Square(File.E, Rank.R2))) shouldBe Some(Some(Piece.WhitePawn))
    board.map(_.pieceAt(Square(File.E, Rank.R7))) shouldBe Some(Some(Piece.BlackPawn))
    board.map(_.pieceAt(Square(File.E, Rank.R1))) shouldBe Some(Some(Piece.WhiteKing))
    board.map(_.pieceAt(Square(File.E, Rank.R8))) shouldBe Some(Some(Piece.BlackKing))

  test("parseBoard: empty board has no pieces"):
    val fen = "8/8/8/8/8/8/8/8"
    val board = FenParser.parseBoard(fen)

    board shouldBe defined
    board.get.pieces.size shouldBe 0

  test("parseBoard: returns None for missing rank (only 7 ranks)"):
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP"
    val board = FenParser.parseBoard(fen)

    board shouldBe empty

  test("parseBoard: returns None for invalid piece character"):
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNX"
    val board = FenParser.parseBoard(fen)

    board shouldBe empty

  test("parseBoard: partial position with two kings placed correctly"):
    val fen = "8/8/4k3/8/4K3/8/8/8"
    val board = FenParser.parseBoard(fen)

    board.map(_.pieceAt(Square(File.E, Rank.R6))) shouldBe Some(Some(Piece.BlackKing))
    board.map(_.pieceAt(Square(File.E, Rank.R4))) shouldBe Some(Some(Piece.WhiteKing))

  test("testRoundTripInitialPosition"):
    val originalFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
    val board = FenParser.parseBoard(originalFen)
    val exportedFen = board.map(FenExporter.boardToFen)

    exportedFen shouldBe Some(originalFen)

  test("testRoundTripEmptyBoard"):
    val originalFen = "8/8/8/8/8/8/8/8"
    val board = FenParser.parseBoard(originalFen)
    val exportedFen = board.map(FenExporter.boardToFen)

    exportedFen shouldBe Some(originalFen)

  test("testRoundTripPartialPosition"):
    val originalFen = "8/8/4k3/8/4K3/8/8/8"
    val board = FenParser.parseBoard(originalFen)
    val exportedFen = board.map(FenExporter.boardToFen)

    exportedFen shouldBe Some(originalFen)

  test("parse full FEN - initial position"):
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val gameState = FenParser.parseFen(fen)

    gameState.isDefined shouldBe true
    gameState.get.activeColor shouldBe Color.White
    gameState.get.castlingWhite.kingSide shouldBe true
    gameState.get.castlingWhite.queenSide shouldBe true
    gameState.get.castlingBlack.kingSide shouldBe true
    gameState.get.castlingBlack.queenSide shouldBe true
    gameState.get.enPassantTarget shouldBe None
    gameState.get.halfMoveClock shouldBe 0
    gameState.get.fullMoveNumber shouldBe 1

  test("parse full FEN - after e4"):
    val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    val gameState = FenParser.parseFen(fen)

    gameState.get.activeColor shouldBe Color.Black
    gameState.get.enPassantTarget shouldBe Some(Square(File.E, Rank.R3))

  test("parse full FEN - invalid parts count"):
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq"
    val gameState = FenParser.parseFen(fen)

    gameState.isDefined shouldBe false

  test("parse full FEN - invalid color"):
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1"
    val gameState = FenParser.parseFen(fen)

    gameState.isDefined shouldBe false

  test("parse full FEN - invalid castling"):
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w XYZ - 0 1"
    val gameState = FenParser.parseFen(fen)

    gameState.isDefined shouldBe false

  test("parseFen: castling '-' produces CastlingRights.None for both sides"):
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1"
    val gameState = FenParser.parseFen(fen)

    gameState.isDefined shouldBe true
    gameState.get.castlingWhite.kingSide shouldBe false
    gameState.get.castlingWhite.queenSide shouldBe false
    gameState.get.castlingBlack.kingSide shouldBe false
    gameState.get.castlingBlack.queenSide shouldBe false

  test("parseBoard: returns None when a rank has too many files (overflow beyond 8)"):
    // "9" alone would advance fileIdx to 9, exceeding 8 → None
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN9"
    val board = FenParser.parseBoard(fen)

    board shouldBe empty

  test("parseBoard: returns None when a rank fails to parse (invalid middle rank)"):
    // Invalid character 'X' in rank 4 should cause failure
    val fen = "rnbqkbnr/pppppppp/8/8/XXXXXXXX/8/PPPPPPPP/RNBQKBNR"
    val board = FenParser.parseBoard(fen)

    board shouldBe empty

  test("parseBoard: returns None when a rank has 9 piece characters (fileIdx > 7)"):
    // 9 pawns in one rank triggers fileIdx > 7 guard (line 78)
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/PPPPPPPPP"
    val board = FenParser.parseBoard(fen)

    board shouldBe empty
