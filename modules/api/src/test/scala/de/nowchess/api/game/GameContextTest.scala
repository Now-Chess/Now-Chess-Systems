package de.nowchess.api.game

import de.nowchess.api.board.{Board, CastlingRights, Color, File, Rank, Square}
import de.nowchess.api.move.Move
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameContextTest extends AnyFunSuite with Matchers:

  test("GameContext.initial exposes expected default state"):
    val initial = GameContext.initial

    initial.board shouldBe Board.initial
    initial.turn shouldBe Color.White
    initial.castlingRights shouldBe CastlingRights.Initial
    initial.enPassantSquare shouldBe None
    initial.halfMoveClock shouldBe 0
    initial.moves shouldBe List.empty

  test("withBoard updates only board"):
    val square       = Square(File.E, Rank.R4)
    val updatedBoard = Board.initial.updated(square, de.nowchess.api.board.Piece.WhiteQueen)
    val updated      = GameContext.initial.withBoard(updatedBoard)
    updated.board shouldBe updatedBoard
    updated.turn shouldBe GameContext.initial.turn
    updated.castlingRights shouldBe GameContext.initial.castlingRights
    updated.enPassantSquare shouldBe GameContext.initial.enPassantSquare
    updated.halfMoveClock shouldBe GameContext.initial.halfMoveClock
    updated.moves shouldBe GameContext.initial.moves

  test("withers update only targeted fields"):
    val initial = GameContext.initial
    val rights = CastlingRights(
      whiteKingSide = true,
      whiteQueenSide = false,
      blackKingSide = false,
      blackQueenSide = true,
    )
    val square        = Some(Square(File.E, Rank.R3))
    val updatedTurn   = initial.withTurn(Color.Black)
    val updatedRights = initial.withCastlingRights(rights)
    val updatedEp     = initial.withEnPassantSquare(square)
    val updatedClock  = initial.withHalfMoveClock(17)

    updatedTurn.turn shouldBe Color.Black
    updatedTurn.board shouldBe initial.board

    updatedRights.castlingRights shouldBe rights
    updatedRights.turn shouldBe initial.turn

    updatedEp.enPassantSquare shouldBe square
    updatedEp.castlingRights shouldBe initial.castlingRights

    updatedClock.halfMoveClock shouldBe 17
    updatedClock.moves shouldBe initial.moves

  test("withMove appends move to history"):
    val move = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4))
    GameContext.initial.withMove(move).moves shouldBe List(move)
