package de.nowchess.chess.command

import de.nowchess.api.board.{Square, File, Rank}
import de.nowchess.api.game.GameContext
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CommandInvokerBranchTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)

  private case class FailingCommand() extends Command:
    override def execute(): Boolean = false
    override def undo(): Boolean = false
    override def description: String = "Failing command"

  private case class ConditionalFailCommand(var shouldFailOnUndo: Boolean = false, var shouldFailOnExecute: Boolean = false) extends Command:
    override def execute(): Boolean = !shouldFailOnExecute
    override def undo(): Boolean = !shouldFailOnUndo
    override def description: String = "Conditional fail"

  private def createMoveCommand(from: Square, to: Square, executeSucceeds: Boolean = true): MoveCommand =
    MoveCommand(
      from = from,
      to = to,
      moveResult = if executeSucceeds then Some(MoveResult.Successful(GameContext.initial, None)) else None,
      previousContext = Some(GameContext.initial)
    )

  test("execute rejects failing commands and keeps history unchanged"):
    val invoker = new CommandInvoker()
    val cmd = FailingCommand()
    invoker.execute(cmd) shouldBe false
    invoker.history.size shouldBe 0
    invoker.getCurrentIndex shouldBe -1

    val failingCmd = FailingCommand()
    val successCmd = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    invoker.execute(failingCmd) shouldBe false
    invoker.history.size shouldBe 0
    invoker.execute(successCmd) shouldBe true
    invoker.history.size shouldBe 1
    invoker.history.head shouldBe successCmd

  test("undo redo and history trimming cover all command state transitions"):
    {
      val invoker = new CommandInvoker()
      invoker.undo() shouldBe false
      invoker.canUndo shouldBe false
      invoker.undo() shouldBe false
    }

    {
      val invoker = new CommandInvoker()
      val cmd1 = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
      val cmd2 = createMoveCommand(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
      invoker.execute(cmd1)
      invoker.execute(cmd2)
      invoker.undo()
      invoker.undo()
      invoker.undo() shouldBe false
    }

    {
      val invoker = new CommandInvoker()
      val failingUndoCmd = ConditionalFailCommand(shouldFailOnUndo = true)
      invoker.execute(failingUndoCmd) shouldBe true
      invoker.canUndo shouldBe true
      invoker.undo() shouldBe false
      invoker.getCurrentIndex shouldBe 0
    }

    {
      val invoker = new CommandInvoker()
      val successUndoCmd = ConditionalFailCommand()
      invoker.execute(successUndoCmd) shouldBe true
      invoker.undo() shouldBe true
      invoker.getCurrentIndex shouldBe -1
    }

    {
      val invoker = new CommandInvoker()
      invoker.redo() shouldBe false
    }

    {
      val invoker = new CommandInvoker()
      val cmd = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
      invoker.execute(cmd)
      invoker.canRedo shouldBe false
      invoker.redo() shouldBe false
    }

    {
      val invoker = new CommandInvoker()
      val cmd1 = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
      val redoFailCmd = ConditionalFailCommand()
      invoker.execute(cmd1)
      invoker.execute(redoFailCmd)
      invoker.undo()
      invoker.canRedo shouldBe true
      redoFailCmd.shouldFailOnExecute = true
      invoker.redo() shouldBe false
      invoker.getCurrentIndex shouldBe 0
    }

    {
      val invoker = new CommandInvoker()
      val cmd = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
      invoker.execute(cmd) shouldBe true
      invoker.undo() shouldBe true
      invoker.redo() shouldBe true
      invoker.getCurrentIndex shouldBe 0
    }

    {
      val invoker = new CommandInvoker()
      val cmd1 = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
      val cmd2 = createMoveCommand(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
      val cmd3 = createMoveCommand(sq(File.D, Rank.R2), sq(File.D, Rank.R4))
      invoker.execute(cmd1)
      invoker.execute(cmd2)
      invoker.undo()
      invoker.canRedo shouldBe true
      invoker.execute(cmd3)
      invoker.canRedo shouldBe false
      invoker.history.size shouldBe 2
      invoker.history(1) shouldBe cmd3
    }

    {
      val invoker = new CommandInvoker()
      val cmd1 = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
      val cmd2 = createMoveCommand(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
      val cmd3 = createMoveCommand(sq(File.G, Rank.R1), sq(File.F, Rank.R3))
      val cmd4 = createMoveCommand(sq(File.D, Rank.R2), sq(File.D, Rank.R4))
      invoker.execute(cmd1)
      invoker.execute(cmd2)
      invoker.execute(cmd3)
      invoker.execute(cmd4)
      invoker.undo()
      invoker.undo()
      invoker.canRedo shouldBe true
      val newCmd = createMoveCommand(sq(File.B, Rank.R2), sq(File.B, Rank.R4))
      invoker.execute(newCmd)
      invoker.history.size shouldBe 3
      invoker.canRedo shouldBe false
    }
