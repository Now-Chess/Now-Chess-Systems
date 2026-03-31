package de.nowchess.chess.command

import de.nowchess.api.board.{Square, File, Rank, Board, Color}
import de.nowchess.chess.logic.GameHistory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CommandInvokerBranchTest extends AnyFunSuite with Matchers:
  
  private def sq(f: File, r: Rank): Square = Square(f, r)

  // ──── Helper: Command that always fails ────
  private case class FailingCommand() extends Command:
    override def execute(): Boolean = false
    override def undo(): Boolean = false
    override def description: String = "Failing command"

  // ──── Helper: Command that conditionally fails on undo or execute ────
  private case class ConditionalFailCommand(var shouldFailOnUndo: Boolean = false, var shouldFailOnExecute: Boolean = false) extends Command:
    override def execute(): Boolean = !shouldFailOnExecute
    override def undo(): Boolean = !shouldFailOnUndo
    override def description: String = "Conditional fail"

  private def createMoveCommand(from: Square, to: Square, executeSucceeds: Boolean = true): MoveCommand =
    val cmd = MoveCommand(
      from = from,
      to = to,
      moveResult = if executeSucceeds then Some(MoveResult.Successful(Board.initial, GameHistory.empty, Color.White, None)) else None,
      previousBoard = Some(Board.initial),
      previousHistory = Some(GameHistory.empty),
      previousTurn = Some(Color.White)
    )
    cmd

  // ──── BRANCH: execute() returns false ────
  test("CommandInvoker.execute() with failing command returns false"):
    val invoker = new CommandInvoker()
    val cmd = FailingCommand()
    invoker.execute(cmd) shouldBe false
    invoker.history.size shouldBe 0
    invoker.getCurrentIndex shouldBe -1

  test("CommandInvoker.execute() does not add failed command to history"):
    val invoker = new CommandInvoker()
    val failingCmd = FailingCommand()
    val successCmd = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    
    invoker.execute(failingCmd) shouldBe false
    invoker.history.size shouldBe 0
    
    invoker.execute(successCmd) shouldBe true
    invoker.history.size shouldBe 1
    invoker.history(0) shouldBe successCmd

  // ──── BRANCH: undo() with invalid index (currentIndex < 0) ────
  test("CommandInvoker.undo() returns false when currentIndex < 0"):
    val invoker = new CommandInvoker()
    // currentIndex starts at -1
    invoker.undo() shouldBe false

  test("CommandInvoker.undo() returns false when empty history"):
    val invoker = new CommandInvoker()
    invoker.canUndo shouldBe false
    invoker.undo() shouldBe false

  // ──── BRANCH: undo() with invalid index (currentIndex >= size) ────
  test("CommandInvoker.undo() returns false when currentIndex >= history size"):
    val invoker = new CommandInvoker()
    val cmd1 = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    val cmd2 = createMoveCommand(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
    
    invoker.execute(cmd1)
    invoker.execute(cmd2)
    // currentIndex now = 1, history.size = 2
    
    invoker.undo()  // currentIndex becomes 0
    invoker.undo()  // currentIndex becomes -1
    invoker.undo()  // currentIndex still -1, should fail

  // ──── BRANCH: undo() command returns false ────
  test("CommandInvoker.undo() returns false when command.undo() fails"):
    val invoker = new CommandInvoker()
    val failingCmd = ConditionalFailCommand(shouldFailOnUndo = true)
    
    invoker.execute(failingCmd) shouldBe true
    invoker.canUndo shouldBe true
    
    invoker.undo() shouldBe false
    // Index should not change when undo fails
    invoker.getCurrentIndex shouldBe 0

  test("CommandInvoker.undo() returns true when command.undo() succeeds"):
    val invoker = new CommandInvoker()
    val successCmd = ConditionalFailCommand(shouldFailOnUndo = false)
    
    invoker.execute(successCmd) shouldBe true
    invoker.undo() shouldBe true
    invoker.getCurrentIndex shouldBe -1

  // ──── BRANCH: redo() with invalid index (currentIndex + 1 >= size) ────
  test("CommandInvoker.redo() returns false when nothing to redo"):
    val invoker = new CommandInvoker()
    invoker.redo() shouldBe false

  test("CommandInvoker.redo() returns false when at end of history"):
    val invoker = new CommandInvoker()
    val cmd = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    
    invoker.execute(cmd)
    // currentIndex = 0, history.size = 1
    invoker.canRedo shouldBe false
    invoker.redo() shouldBe false

  test("CommandInvoker.redo() returns false when currentIndex + 1 >= size"):
    val invoker = new CommandInvoker()
    val cmd1 = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    val cmd2 = createMoveCommand(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
    
    invoker.execute(cmd1)
    invoker.execute(cmd2)
    // currentIndex = 1, size = 2, currentIndex + 1 = 2, so 2 < 2 is false
    invoker.canRedo shouldBe false
    invoker.redo() shouldBe false

  // ──── BRANCH: redo() command returns false ────
  test("CommandInvoker.redo() returns false when command.execute() fails"):
    val invoker = new CommandInvoker()
    val cmd1 = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    val redoFailCmd = ConditionalFailCommand(shouldFailOnExecute = false)  // Succeeds on first execute
    
    invoker.execute(cmd1)
    invoker.execute(redoFailCmd)  // Succeeds and added to history
    
    invoker.undo()
    // currentIndex = 0, redoFailCmd is at index 1
    invoker.canRedo shouldBe true
    
    // Now modify to fail on next execute (redo)
    redoFailCmd.shouldFailOnExecute = true
    invoker.redo() shouldBe false
    // currentIndex should not change
    invoker.getCurrentIndex shouldBe 0

  test("CommandInvoker.redo() returns true when command.execute() succeeds"):
    val invoker = new CommandInvoker()
    val cmd = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    
    invoker.execute(cmd) shouldBe true
    invoker.undo() shouldBe true
    invoker.redo() shouldBe true
    invoker.getCurrentIndex shouldBe 0

  // ──── BRANCH: execute() with redo history discarding (while loop) ────
  test("CommandInvoker.execute() discards redo history via while loop"):
    val invoker = new CommandInvoker()
    val cmd1 = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    val cmd2 = createMoveCommand(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
    val cmd3 = createMoveCommand(sq(File.D, Rank.R2), sq(File.D, Rank.R4))
    
    invoker.execute(cmd1)
    invoker.execute(cmd2)
    // currentIndex = 1, size = 2
    
    invoker.undo()
    // currentIndex = 0, size = 2
    // Redo history exists: cmd2 is at index 1
    invoker.canRedo shouldBe true
    
    invoker.execute(cmd3)
    // while loop should discard cmd2
    invoker.canRedo shouldBe false
    invoker.history.size shouldBe 2
    invoker.history(1) shouldBe cmd3

  test("CommandInvoker.execute() discards multiple redo commands"):
    val invoker = new CommandInvoker()
    val cmd1 = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    val cmd2 = createMoveCommand(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
    val cmd3 = createMoveCommand(sq(File.G, Rank.R1), sq(File.F, Rank.R3))
    val cmd4 = createMoveCommand(sq(File.D, Rank.R2), sq(File.D, Rank.R4))
    
    invoker.execute(cmd1)
    invoker.execute(cmd2)
    invoker.execute(cmd3)
    invoker.execute(cmd4)
    // currentIndex = 3, size = 4
    
    invoker.undo()
    invoker.undo()
    // currentIndex = 1, size = 4
    // Redo history: cmd3 (idx 2), cmd4 (idx 3)
    invoker.canRedo shouldBe true
    
    val newCmd = createMoveCommand(sq(File.B, Rank.R2), sq(File.B, Rank.R4))
    invoker.execute(newCmd)
    // While loop should discard indices 2 and 3 (cmd3 and cmd4)
    invoker.history.size shouldBe 3
    invoker.canRedo shouldBe false

  // ──── BRANCH: execute() with no redo history to discard ────
  test("CommandInvoker.execute() with no redo history (while condition false)"):
    val invoker = new CommandInvoker()
    val cmd1 = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    val cmd2 = createMoveCommand(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
    
    invoker.execute(cmd1)
    invoker.execute(cmd2)
    // currentIndex = 1, size = 2
    // currentIndex < size - 1 is 1 < 1 which is false, so while loop doesn't run
    
    invoker.canRedo shouldBe false
    
    val cmd3 = createMoveCommand(sq(File.D, Rank.R2), sq(File.D, Rank.R4))
    invoker.execute(cmd3)  // While loop condition should be false, no iterations
    invoker.history.size shouldBe 3

