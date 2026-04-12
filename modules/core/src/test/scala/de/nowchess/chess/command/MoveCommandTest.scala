package de.nowchess.chess.command

import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.api.game.GameContext
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MoveCommandTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)

  test("MoveCommand defaults to empty optional state and false execute/undo"):
    val cmd = MoveCommand(from = sq(File.E, Rank.R2), to = sq(File.E, Rank.R4))
    cmd.moveResult shouldBe None
    cmd.previousContext shouldBe None
    cmd.execute() shouldBe false
    cmd.undo() shouldBe false
    cmd.description shouldBe "Move from e2 to e4"

  test("MoveCommand execute/undo succeed when state is present"):
    val executable = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4),
      moveResult = Some(MoveResult.Successful(GameContext.initial, None)),
    )
    executable.execute() shouldBe true

    val undoable = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4),
      moveResult = Some(MoveResult.Successful(GameContext.initial, None)),
      previousContext = Some(GameContext.initial),
    )
    undoable.undo() shouldBe true

  test("MoveCommand is immutable and preserves equality/hash semantics"):
    val cmd1 = MoveCommand(from = sq(File.E, Rank.R2), to = sq(File.E, Rank.R4))

    val result = MoveResult.Successful(GameContext.initial, None)
    val cmd2 = cmd1.copy(
      moveResult = Some(result),
      previousContext = Some(GameContext.initial),
    )

    cmd1.moveResult shouldBe None
    cmd1.previousContext shouldBe None

    cmd2.moveResult shouldBe Some(result)
    cmd2.previousContext shouldBe Some(GameContext.initial)

    val eq1 = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4),
      moveResult = None,
      previousContext = None,
    )

    val eq2 = MoveCommand(
      from = sq(File.E, Rank.R2),
      to = sq(File.E, Rank.R4),
      moveResult = None,
      previousContext = None,
    )

    eq1 shouldBe eq2
    eq1.hashCode shouldBe eq2.hashCode

    val hash1 = eq1.hashCode
    val hash2 = eq1.hashCode
    hash1 shouldBe hash2
