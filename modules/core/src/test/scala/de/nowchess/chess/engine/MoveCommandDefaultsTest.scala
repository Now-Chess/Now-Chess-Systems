package de.nowchess.chess.command

import de.nowchess.api.board.{Square, File, Rank, Board, Color}
import de.nowchess.chess.logic.GameHistory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MoveCommandDefaultsTest extends AnyFunSuite with Matchers:
  
  private def sq(f: File, r: Rank): Square = Square(f, r)

  // Tests for MoveCommand with default parameter values
  test("MoveCommand with no moveResult defaults to None"):
    val cmd = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4)
    )
    cmd.moveResult shouldBe None
    cmd.execute() shouldBe false

  test("MoveCommand with no previousBoard defaults to None"):
    val cmd = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4)
    )
    cmd.previousBoard shouldBe None
    cmd.undo() shouldBe false

  test("MoveCommand with no previousHistory defaults to None"):
    val cmd = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4)
    )
    cmd.previousHistory shouldBe None
    cmd.undo() shouldBe false

  test("MoveCommand with no previousTurn defaults to None"):
    val cmd = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4)
    )
    cmd.previousTurn shouldBe None
    cmd.undo() shouldBe false

  test("MoveCommand description is always returned"):
    val cmd = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4)
    )
    cmd.description shouldBe "Move from e2 to e4"

  test("MoveCommand execute returns false when moveResult is None"):
    val cmd = MoveCommand(
      from = sq(File.A, Rank.R1),
      to = sq(File.B, Rank.R3)
    )
    cmd.execute() shouldBe false

  test("MoveCommand undo returns false when any previous state is None"):
    // Missing previousBoard
    val cmd1 = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4),
      moveResult = Some(MoveResult.Successful(Board.initial, GameHistory.empty, Color.White, None)),
      previousBoard = None,
      previousHistory = Some(GameHistory.empty),
      previousTurn = Some(Color.White)
    )
    cmd1.undo() shouldBe false

    // Missing previousHistory
    val cmd2 = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4),
      moveResult = Some(MoveResult.Successful(Board.initial, GameHistory.empty, Color.White, None)),
      previousBoard = Some(Board.initial),
      previousHistory = None,
      previousTurn = Some(Color.White)
    )
    cmd2.undo() shouldBe false

    // Missing previousTurn
    val cmd3 = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4),
      moveResult = Some(MoveResult.Successful(Board.initial, GameHistory.empty, Color.White, None)),
      previousBoard = Some(Board.initial),
      previousHistory = Some(GameHistory.empty),
      previousTurn = None
    )
    cmd3.undo() shouldBe false

  test("MoveCommand execute returns true when moveResult is defined"):
    val cmd = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4),
      moveResult = Some(MoveResult.Successful(Board.initial, GameHistory.empty, Color.White, None))
    )
    cmd.execute() shouldBe true

  test("MoveCommand undo returns true when all previous states are defined"):
    val cmd = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4),
      moveResult = Some(MoveResult.Successful(Board.initial, GameHistory.empty, Color.White, None)),
      previousBoard = Some(Board.initial),
      previousHistory = Some(GameHistory.empty),
      previousTurn = Some(Color.White)
    )
    cmd.undo() shouldBe true
