package de.nowchess.chess.command

import de.nowchess.api.board.{Square, File, Rank, Board, Color}
import de.nowchess.chess.logic.GameHistory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CommandInvokerTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)
  
  private def createMoveCommand(from: Square, to: Square): MoveCommand =
    MoveCommand(
      from = from,
      to = to,
      moveResult = Some(MoveResult.Successful(Board.initial, GameHistory.empty, Color.White, None)),
      previousBoard = Some(Board.initial),
      previousHistory = Some(GameHistory.empty),
      previousTurn = Some(Color.White)
    )

  test("CommandInvoker executes a command and adds it to history"):
    val invoker = new CommandInvoker()
    val cmd = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    invoker.execute(cmd) shouldBe true
    invoker.history.size shouldBe 1
    invoker.getCurrentIndex shouldBe 0

  test("CommandInvoker executes multiple commands in sequence"):
    val invoker = new CommandInvoker()
    val cmd1 = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    val cmd2 = createMoveCommand(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
    invoker.execute(cmd1) shouldBe true
    invoker.execute(cmd2) shouldBe true
    invoker.history.size shouldBe 2
    invoker.getCurrentIndex shouldBe 1

  test("CommandInvoker.canUndo returns false when empty"):
    val invoker = new CommandInvoker()
    invoker.canUndo shouldBe false

  test("CommandInvoker.canUndo returns true after execution"):
    val invoker = new CommandInvoker()
    val cmd = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    invoker.execute(cmd)
    invoker.canUndo shouldBe true

  test("CommandInvoker.undo decrements current index"):
    val invoker = new CommandInvoker()
    val cmd = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    invoker.execute(cmd)
    invoker.getCurrentIndex shouldBe 0
    invoker.undo() shouldBe true
    invoker.getCurrentIndex shouldBe -1

  test("CommandInvoker.canRedo returns true after undo"):
    val invoker = new CommandInvoker()
    val cmd = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    invoker.execute(cmd)
    invoker.undo()
    invoker.canRedo shouldBe true

  test("CommandInvoker.redo re-executes a command"):
    val invoker = new CommandInvoker()
    val cmd = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    invoker.execute(cmd)
    invoker.undo() shouldBe true
    invoker.redo() shouldBe true
    invoker.getCurrentIndex shouldBe 0

  test("CommandInvoker.canUndo returns false when at beginning"):
    val invoker = new CommandInvoker()
    val cmd = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    invoker.execute(cmd)
    invoker.undo()
    invoker.canUndo shouldBe false

  test("CommandInvoker clear removes all history"):
    val invoker = new CommandInvoker()
    val cmd = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    invoker.execute(cmd)
    invoker.clear()
    invoker.history.size shouldBe 0
    invoker.getCurrentIndex shouldBe -1

  test("CommandInvoker discards all history when executing after undoing all"):
    val invoker = new CommandInvoker()
    val cmd1 = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    val cmd2 = createMoveCommand(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
    val cmd3 = createMoveCommand(sq(File.D, Rank.R2), sq(File.D, Rank.R4))
    invoker.execute(cmd1)
    invoker.execute(cmd2)
    invoker.undo()
    invoker.undo()
    // After undoing twice, we're at the beginning (before any commands)
    invoker.getCurrentIndex shouldBe -1
    invoker.canRedo shouldBe true
    // Executing a new command from the beginning discards all redo history
    invoker.execute(cmd3)
    invoker.canRedo shouldBe false
    invoker.history.size shouldBe 1
    invoker.history(0) shouldBe cmd3
    invoker.getCurrentIndex shouldBe 0

  test("CommandInvoker discards redo history when executing mid-history"):
    val invoker = new CommandInvoker()
    val cmd1 = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    val cmd2 = createMoveCommand(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
    val cmd3 = createMoveCommand(sq(File.D, Rank.R2), sq(File.D, Rank.R4))
    invoker.execute(cmd1)
    invoker.execute(cmd2)
    invoker.undo()
    // After one undo, we're at the end of cmd1
    invoker.getCurrentIndex shouldBe 0
    invoker.canRedo shouldBe true
    // Executing a new command discards cmd2 (the redo history)
    invoker.execute(cmd3)
    invoker.canRedo shouldBe false
    invoker.history.size shouldBe 2
    invoker.history(0) shouldBe cmd1
    invoker.history(1) shouldBe cmd3
    invoker.getCurrentIndex shouldBe 1

