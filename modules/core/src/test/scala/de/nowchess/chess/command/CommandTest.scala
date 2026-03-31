package de.nowchess.chess.command

import de.nowchess.api.board.{Board, Color}
import de.nowchess.chess.logic.GameHistory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CommandTest extends AnyFunSuite with Matchers:

  test("QuitCommand can be created"):
    val cmd = QuitCommand()
    cmd shouldNot be(null)

  test("QuitCommand execute returns true"):
    val cmd = QuitCommand()
    cmd.execute() shouldBe true

  test("QuitCommand undo returns false (cannot undo quit)"):
    val cmd = QuitCommand()
    cmd.undo() shouldBe false

  test("QuitCommand description"):
    val cmd = QuitCommand()
    cmd.description shouldBe "Quit game"

  test("ResetCommand with no prior state"):
    val cmd = ResetCommand()
    cmd.execute() shouldBe true
    cmd.undo() shouldBe false

  test("ResetCommand with prior state can undo"):
    val cmd = ResetCommand(
      previousBoard = Some(Board.initial),
      previousHistory = Some(GameHistory.empty),
      previousTurn = Some(Color.White)
    )
    cmd.execute() shouldBe true
    cmd.undo() shouldBe true

  test("ResetCommand with partial state cannot undo"):
    val cmd = ResetCommand(
      previousBoard = Some(Board.initial),
      previousHistory = None,  // missing
      previousTurn = Some(Color.White)
    )
    cmd.execute() shouldBe true
    cmd.undo() shouldBe false

  test("ResetCommand description"):
    val cmd = ResetCommand()
    cmd.description shouldBe "Reset board"

