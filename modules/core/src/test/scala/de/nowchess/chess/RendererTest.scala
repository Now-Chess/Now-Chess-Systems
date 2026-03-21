package de.nowchess.chess

import de.nowchess.api.board.Board
import de.nowchess.chess.view.Renderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RendererTest:

  @Test def renderContainsFileLabels(): Unit =
    val output = Renderer.render(Board.initial)
    assertTrue(output.contains("a"), "render output should contain file label 'a'")
    assertTrue(output.contains("h"), "render output should contain file label 'h'")

  @Test def renderContainsRankLabels(): Unit =
    val output = Renderer.render(Board.initial)
    assertTrue(output.contains("1"), "render output should contain rank label '1'")
    assertTrue(output.contains("8"), "render output should contain rank label '8'")

  @Test def renderContainsWhiteKingUnicode(): Unit =
    val output = Renderer.render(Board.initial)
    assertTrue(output.contains("\u2654"), "render output should contain white king \u2654")

  @Test def renderContainsBlackQueenUnicode(): Unit =
    val output = Renderer.render(Board.initial)
    assertTrue(output.contains("\u265B"), "render output should contain black queen \u265B")

  @Test def renderContainsAnsiReset(): Unit =
    val output = Renderer.render(Board.initial)
    assertTrue(output.contains("\u001b[0m"), "render output should contain ANSI reset code")

  @Test def renderReturnsStringNotUnit(): Unit =
    // Compilation-time guarantee, but verify non-empty at runtime
    val output = Renderer.render(Board.initial)
    assertTrue(output.nonEmpty)
