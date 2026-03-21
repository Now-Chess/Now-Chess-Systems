package de.nowchess.chess.view

import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Rank, Square}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RendererExtendedTest:

  private def boardOf(pieces: (Square, Piece)*): Board = Board(pieces.toMap)
  private def sq(file: File, rank: Rank): Square = Square(file, rank)

  // ── Empty board ────────────────────────────────────────────────────────

  @Test def renderEmptyBoardContainsBoardFrame(): Unit =
    val emptyBoard = boardOf()
    val output = Renderer.render(emptyBoard)
    assertTrue(output.contains("a"), "Should contain file 'a'")
    assertTrue(output.contains("h"), "Should contain file 'h'")
    assertTrue(output.contains("1"), "Should contain rank '1'")
    assertTrue(output.contains("8"), "Should contain rank '8'")

  @Test def renderEmptyBoardIsNotEmpty(): Unit =
    val emptyBoard = boardOf()
    val output = Renderer.render(emptyBoard)
    assertTrue(output.nonEmpty)

  @Test def renderEmptyBoardContainsAnsiColors(): Unit =
    val emptyBoard = boardOf()
    val output = Renderer.render(emptyBoard)
    assertTrue(output.contains("\u001b[48;5;223m") || output.contains("\u001b[48;5;130m"))

  // ── Single piece placement ─────────────────────────────────────────────

  @Test def renderBoardWithSingleWhitePawn(): Unit =
    val board = boardOf(sq(File.E, Rank.R2) -> Piece.WhitePawn)
    val output = Renderer.render(board)
    assertTrue(output.contains("\u2659"), "Should contain white pawn unicode")

  @Test def renderBoardWithSingleBlackKing(): Unit =
    val board = boardOf(sq(File.E, Rank.R8) -> Piece.BlackKing)
    val output = Renderer.render(board)
    assertTrue(output.contains("\u265A"), "Should contain black king unicode")

  @Test def renderBoardWithMultiplePieces(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.D, Rank.R8) -> Piece.BlackQueen,
      sq(File.A, Rank.R1) -> Piece.WhiteRook
    )
    val output = Renderer.render(board)
    assertTrue(output.contains("\u2654")) // white king
    assertTrue(output.contains("\u265B")) // black queen
    assertTrue(output.contains("\u2656")) // white rook

  // ── All pieces on board ────────────────────────────────────────────────

  @Test def renderInitialBoardHasAllPieceTypes(): Unit =
    val output = Renderer.render(Board.initial)
    // White pieces
    assertTrue(output.contains("\u2654"), "white king")
    assertTrue(output.contains("\u2655"), "white queen")
    assertTrue(output.contains("\u2656"), "white rook")
    assertTrue(output.contains("\u2657"), "white bishop")
    assertTrue(output.contains("\u2658"), "white knight")
    assertTrue(output.contains("\u2659"), "white pawn")
    // Black pieces
    assertTrue(output.contains("\u265A"), "black king")
    assertTrue(output.contains("\u265B"), "black queen")
    assertTrue(output.contains("\u265C"), "black rook")
    assertTrue(output.contains("\u265D"), "black bishop")
    assertTrue(output.contains("\u265E"), "black knight")
    assertTrue(output.contains("\u265F"), "black pawn")

  // ── Board dimensions ──────────────────────────────────────────────────

  @Test def renderIncludesAllFileLabels(): Unit =
    val output = Renderer.render(Board.initial)
    for file <- Seq("a", "b", "c", "d", "e", "f", "g", "h") do
      assertTrue(output.contains(file))

  @Test def renderIncludesAllRankLabels(): Unit =
    val output = Renderer.render(Board.initial)
    for rank <- 1 to 8 do
      assertTrue(output.contains(rank.toString))

  // ── Piece placement accuracy ───────────────────────────────────────────

  @Test def renderCornerPiecesAreIncluded(): Unit =
    val board = boardOf(
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.H, Rank.R1) -> Piece.WhiteRook,
      sq(File.A, Rank.R8) -> Piece.BlackRook,
      sq(File.H, Rank.R8) -> Piece.BlackRook
    )
    val output = Renderer.render(board)
    val rookCount = output.count(_ == '\u2656') + output.count(_ == '\u265C')
    assertEquals(4, rookCount)

  // ── ANSI codes ─────────────────────────────────────────────────────────

  @Test def renderContainsResetCode(): Unit =
    val output = Renderer.render(Board.initial)
    assertTrue(output.contains("\u001b[0m"))

  @Test def renderContainsBackgroundColors(): Unit =
    val output = Renderer.render(Board.initial)
    assertTrue(output.contains("\u001b[48;5;223m") || output.contains("\u001b[48;5;130m"))

  @Test def renderContainsForegroundColors(): Unit =
    val board = Board.initial
    val output = Renderer.render(board)
    // Should have some white text or black text for pieces
    assertTrue(output.contains("\u001b[97m") || output.contains("\u001b[30m"))

  // ── Output consistency ─────────────────────────────────────────────────

  @Test def renderingSameBoardProducesSameOutput(): Unit =
    val board = Board.initial
    val output1 = Renderer.render(board)
    val output2 = Renderer.render(board)
    assertEquals(output1, output2)

  @Test def renderingDifferentBoardsProduceDifferentOutput(): Unit =
    val board1 = Board.initial
    val board2 = boardOf(sq(File.E, Rank.R4) -> Piece.WhitePawn)
    val output1 = Renderer.render(board1)
    val output2 = Renderer.render(board2)
    assertNotEquals(output1, output2)

  // ── Large outputs ──────────────────────────────────────────────────────

  @Test def renderInitialBoardProducesReasonablyLargeOutput(): Unit =
    val output = Renderer.render(Board.initial)
    // Should have multiple lines (8 ranks + labels)
    val lineCount = output.count(_ == '\n')
    assertTrue(lineCount > 8)

  @Test def renderOutputContainsNewlines(): Unit =
    val output = Renderer.render(Board.initial)
    assertTrue(output.contains("\n"))

  // ── Piece color in output ──────────────────────────────────────────────

  @Test def renderBoardWithWhiteAndBlackPieces(): Unit =
    val board = boardOf(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.E, Rank.R8) -> Piece.BlackKing
    )
    val output = Renderer.render(board)
    assertTrue(output.contains("\u2654")) // white king
    assertTrue(output.contains("\u265A")) // black king

  // ── Pawn positions ────────────────────────────────────────────────────

  @Test def renderBoardWithAllWhitePawns(): Unit =
    val pawns = (0 until 8).map(fileIdx =>
      sq(File.values(fileIdx), Rank.R2) -> Piece.WhitePawn
    )
    val board = boardOf(pawns*)
    val output = Renderer.render(board)
    val pawnCount = output.count(_ == '\u2659')
    assertEquals(8, pawnCount)

  @Test def renderBoardWithAllBlackPawns(): Unit =
    val pawns = (0 until 8).map(fileIdx =>
      sq(File.values(fileIdx), Rank.R7) -> Piece.BlackPawn
    )
    val board = boardOf(pawns*)
    val output = Renderer.render(board)
    val pawnCount = output.count(_ == '\u265F')
    assertEquals(8, pawnCount)

  // ── Center of board ───────────────────────────────────────────────────

  @Test def renderBoardWithCenterPiece(): Unit =
    val board = boardOf(sq(File.D, Rank.R4) -> Piece.WhiteQueen)
    val output = Renderer.render(board)
    assertTrue(output.contains("\u2655"))

  // ── Board doesn't mutate ──────────────────────────────────────────────

  @Test def renderingBoardDoesNotMutateIt(): Unit =
    val board = Board.initial
    val pieceBefore = board.pieceAt(sq(File.E, Rank.R1))
    val _ = Renderer.render(board)
    val pieceAfter = board.pieceAt(sq(File.E, Rank.R1))
    assertEquals(pieceBefore, pieceAfter)
