package de.nowchess.chess.command

import de.nowchess.api.board.{Square, File, Rank, Board, Color}
import de.nowchess.chess.logic.GameHistory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MoveCommandImmutabilityTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)

  test("MoveCommand should be immutable - fields cannot be mutated after creation"):
    val cmd1 = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4)
    )

    // Create second command with filled state
    val result = MoveResult.Successful(Board.initial, GameHistory.empty, Color.Black, None)
    val cmd2 = cmd1.copy(
      moveResult = Some(result),
      previousBoard = Some(Board.initial),
      previousHistory = Some(GameHistory.empty),
      previousTurn = Some(Color.White)
    )

    // Original should be unchanged
    cmd1.moveResult shouldBe None
    cmd1.previousBoard shouldBe None
    cmd1.previousHistory shouldBe None
    cmd1.previousTurn shouldBe None

    // New should have values
    cmd2.moveResult shouldBe Some(result)
    cmd2.previousBoard shouldBe Some(Board.initial)
    cmd2.previousHistory shouldBe Some(GameHistory.empty)
    cmd2.previousTurn shouldBe Some(Color.White)

  test("MoveCommand equals and hashCode respect immutability"):
    val cmd1 = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4),
      moveResult = None,
      previousBoard = None,
      previousHistory = None,
      previousTurn = None
    )

    val cmd2 = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4),
      moveResult = None,
      previousBoard = None,
      previousHistory = None,
      previousTurn = None
    )

    // Same values should be equal
    cmd1 shouldBe cmd2
    cmd1.hashCode shouldBe cmd2.hashCode

    // Hash should be consistent (required for use as map keys)
    val hash1 = cmd1.hashCode
    val hash2 = cmd1.hashCode
    hash1 shouldBe hash2
