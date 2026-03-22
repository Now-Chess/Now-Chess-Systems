package de.nowchess.chess.view

import de.nowchess.api.board.{Board, File, Piece, Rank, Square}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RendererTest extends AnyFunSuite with Matchers:

  test("render contains column header with all file labels"):
    Renderer.render(Board.initial) should include("a  b  c  d  e  f  g  h")

  test("render output begins with the column header"):
    Renderer.render(Board.initial) should startWith("  a  b  c  d  e  f  g  h")

  test("render contains rank labels 1 through 8"):
    val output = Renderer.render(Board.initial)
    for rank <- 1 to 8 do output should include(s"$rank ")

  test("render shows white king unicode symbol for initial board"):
    Renderer.render(Board.initial) should include("\u2654")

  test("render shows black king unicode symbol for initial board"):
    Renderer.render(Board.initial) should include("\u265A")

  test("render contains ANSI light-square background code"):
    Renderer.render(Board.initial) should include("\u001b[48;5;223m")

  test("render contains ANSI dark-square background code"):
    Renderer.render(Board.initial) should include("\u001b[48;5;130m")

  test("render uses white-piece foreground color for white pieces"):
    Renderer.render(Board.initial) should include("\u001b[97m")

  test("render uses black-piece foreground color for black pieces"):
    Renderer.render(Board.initial) should include("\u001b[30m")

  test("render of empty board contains no piece unicode"):
    val output = Renderer.render(Board(Map.empty))
    output should include("a  b  c  d  e  f  g  h")
    output should not include "\u2654"
    output should not include "\u265A"
