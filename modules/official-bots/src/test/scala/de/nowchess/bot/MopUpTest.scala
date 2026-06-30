package de.nowchess.bot

import de.nowchess.api.board.{Board, Color, File, Piece, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.bot.bots.nnue.MopUp
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MopUpTest extends AnyFunSuite with Matchers:

  private def ctx(turn: Color, pieces: (Square, Piece)*): GameContext =
    GameContext.initial.withBoard(Board(pieces.toMap)).withTurn(turn)

  private val wk = Square(File.E, Rank.R1) -> Piece.WhiteKing
  private val wq = Square(File.D, Rank.R1) -> Piece.WhiteQueen
  private val bkCorner = Square(File.H, Rank.R8) -> Piece.BlackKing
  private val bkCenter = Square(File.D, Rank.R4) -> Piece.BlackKing

  test("zero in a balanced middlegame-like position (both sides have material)"):
    MopUp.score(ctx(Color.White, wk, wq, bkCorner, Square(File.A, Rank.R8) -> Piece.BlackQueen)) should be(0)

  test("zero when winner lacks mating material (lone king vs king)"):
    MopUp.score(ctx(Color.White, wk, bkCorner)) should be(0)

  test("positive for the winning side to move in KQ vs K"):
    MopUp.score(ctx(Color.White, wk, wq, bkCorner)) should be > 0

  test("negative for the bare-king side to move in KQ vs K"):
    MopUp.score(ctx(Color.Black, wk, wq, bkCorner)) should be < 0

  test("cornered bare king scores higher than centralized bare king"):
    val cornered    = MopUp.score(ctx(Color.White, wk, wq, bkCorner))
    val centralized = MopUp.score(ctx(Color.White, wk, wq, bkCenter))
    cornered should be > centralized
