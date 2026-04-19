package de.nowchess.bot

import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BotMoveRepetitionTest extends AnyFunSuite with Matchers:

  private val move1 = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal())
  private val move2 = Move(Square(File.D, Rank.R2), Square(File.D, Rank.R4), MoveType.Normal())

  test("filterAllowed passes through moves when none are blocked"):
    val ctx     = GameContext.initial
    val allowed = BotMoveRepetition.filterAllowed(ctx, List(move1, move2))
    allowed should contain(move1)
    allowed should contain(move2)

  test("filterAllowed removes the move repeated three times"):
    val ctx     = GameContext.initial.copy(moves = List(move1, move1, move1))
    val allowed = BotMoveRepetition.filterAllowed(ctx, List(move1, move2))
    allowed should not contain move1
    allowed should contain(move2)

  test("filterAllowed keeps all moves when repetition is below threshold"):
    val ctx     = GameContext.initial.copy(moves = List(move1, move1))
    val allowed = BotMoveRepetition.filterAllowed(ctx, List(move1, move2))
    allowed should contain(move1)
    allowed should contain(move2)
