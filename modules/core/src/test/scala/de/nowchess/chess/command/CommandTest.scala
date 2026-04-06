package de.nowchess.chess.command

import de.nowchess.api.game.GameContext
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CommandTest extends AnyFunSuite with Matchers:

  test("QuitCommand properties and behavior"):
    val cmd = QuitCommand()
    cmd shouldNot be(null)
    cmd.execute() shouldBe true
    cmd.undo() shouldBe false
    cmd.description shouldBe "Quit game"

  test("ResetCommand behavior depends on previousContext"):
    val noState = ResetCommand()
    noState.execute() shouldBe true
    noState.undo() shouldBe false
    noState.description shouldBe "Reset board"

    val withState = ResetCommand(previousContext = Some(GameContext.initial))
    withState.execute() shouldBe true
    withState.undo() shouldBe true
