package de.nowchess.chess.view

import de.nowchess.api.board.{Color, Piece, PieceType}
import de.nowchess.chess.view.unicode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PieceUnicodeTest:

  // ── White pieces ───────────────────────────────────────────────────────

  @Test def whiteKingUnicode(): Unit =
    val piece = Piece(Color.White, PieceType.King)
    assertEquals("\u2654", piece.unicode)

  @Test def whiteQueenUnicode(): Unit =
    val piece = Piece(Color.White, PieceType.Queen)
    assertEquals("\u2655", piece.unicode)

  @Test def whiteRookUnicode(): Unit =
    val piece = Piece(Color.White, PieceType.Rook)
    assertEquals("\u2656", piece.unicode)

  @Test def whiteBishopUnicode(): Unit =
    val piece = Piece(Color.White, PieceType.Bishop)
    assertEquals("\u2657", piece.unicode)

  @Test def whiteKnightUnicode(): Unit =
    val piece = Piece(Color.White, PieceType.Knight)
    assertEquals("\u2658", piece.unicode)

  @Test def whitePawnUnicode(): Unit =
    val piece = Piece(Color.White, PieceType.Pawn)
    assertEquals("\u2659", piece.unicode)

  // ── Black pieces ───────────────────────────────────────────────────────

  @Test def blackKingUnicode(): Unit =
    val piece = Piece(Color.Black, PieceType.King)
    assertEquals("\u265A", piece.unicode)

  @Test def blackQueenUnicode(): Unit =
    val piece = Piece(Color.Black, PieceType.Queen)
    assertEquals("\u265B", piece.unicode)

  @Test def blackRookUnicode(): Unit =
    val piece = Piece(Color.Black, PieceType.Rook)
    assertEquals("\u265C", piece.unicode)

  @Test def blackBishopUnicode(): Unit =
    val piece = Piece(Color.Black, PieceType.Bishop)
    assertEquals("\u265D", piece.unicode)

  @Test def blackKnightUnicode(): Unit =
    val piece = Piece(Color.Black, PieceType.Knight)
    assertEquals("\u265E", piece.unicode)

  @Test def blackPawnUnicode(): Unit =
    val piece = Piece(Color.Black, PieceType.Pawn)
    assertEquals("\u265F", piece.unicode)

  // ── Unicode lengths ───────────────────────────────────────────────────

  @Test def eachUnicodeCharacterIsNonEmpty(): Unit =
    val pieces = Seq(
      Piece.WhiteKing, Piece.WhiteQueen, Piece.WhiteRook,
      Piece.WhiteBishop, Piece.WhiteKnight, Piece.WhitePawn,
      Piece.BlackKing, Piece.BlackQueen, Piece.BlackRook,
      Piece.BlackBishop, Piece.BlackKnight, Piece.BlackPawn
    )
    for piece <- pieces do
      assertFalse(piece.unicode.isEmpty)

  @Test def unicodeCharactersAreDistinct(): Unit =
    val unicodes = Set(
      Piece.WhiteKing.unicode, Piece.WhiteQueen.unicode, Piece.WhiteRook.unicode,
      Piece.WhiteBishop.unicode, Piece.WhiteKnight.unicode, Piece.WhitePawn.unicode,
      Piece.BlackKing.unicode, Piece.BlackQueen.unicode, Piece.BlackRook.unicode,
      Piece.BlackBishop.unicode, Piece.BlackKnight.unicode, Piece.BlackPawn.unicode
    )
    assertEquals(12, unicodes.size)

  // ── Convenience constructors ───────────────────────────────────────────

  @Test def pieceConvenienceConstructorsReturnCorrectUnicode(): Unit =
    assertEquals("\u2654", Piece.WhiteKing.unicode)
    assertEquals("\u2655", Piece.WhiteQueen.unicode)
    assertEquals("\u2656", Piece.WhiteRook.unicode)
    assertEquals("\u2657", Piece.WhiteBishop.unicode)
    assertEquals("\u2658", Piece.WhiteKnight.unicode)
    assertEquals("\u2659", Piece.WhitePawn.unicode)
    assertEquals("\u265A", Piece.BlackKing.unicode)
    assertEquals("\u265B", Piece.BlackQueen.unicode)
    assertEquals("\u265C", Piece.BlackRook.unicode)
    assertEquals("\u265D", Piece.BlackBishop.unicode)
    assertEquals("\u265E", Piece.BlackKnight.unicode)
    assertEquals("\u265F", Piece.BlackPawn.unicode)

  // ── Unicode roundtrip ──────────────────────────────────────────────────

  @Test def createPieceAndGetUnicodeConsistently(): Unit =
    val whiteKing = Piece(Color.White, PieceType.King)
    val unicode1 = whiteKing.unicode
    val unicode2 = whiteKing.unicode
    assertEquals(unicode1, unicode2)

  @Test def differentPiecesHaveDifferentUnicodes(): Unit =
    val king = Piece.WhiteKing
    val queen = Piece.WhiteQueen
    assertNotEquals(king.unicode, queen.unicode)

  @Test def sameTypeDifferentColorHaveDifferentUnicodes(): Unit =
    val whitePawn = Piece.WhitePawn
    val blackPawn = Piece.BlackPawn
    assertNotEquals(whitePawn.unicode, blackPawn.unicode)
