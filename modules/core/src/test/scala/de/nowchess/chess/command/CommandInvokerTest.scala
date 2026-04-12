package de.nowchess.chess.command

import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.api.game.GameContext
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CommandInvokerTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)

  private def createMoveCommand(from: Square, to: Square): MoveCommand =
    MoveCommand(
      from = from,
      to = to,
      moveResult = Some(MoveResult.Successful(GameContext.initial, None)),
      previousContext = Some(GameContext.initial),
    )

  test("execute appends commands and updates index"):
    val invoker = new CommandInvoker()
    val cmd     = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    invoker.execute(cmd) shouldBe true
    invoker.history.size shouldBe 1
    invoker.getCurrentIndex shouldBe 0

    val cmd2 = createMoveCommand(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
    invoker.execute(cmd2) shouldBe true
    invoker.history.size shouldBe 2
    invoker.getCurrentIndex shouldBe 1

  test("undo and redo update index and availability flags"):
    val invoker = new CommandInvoker()
    val cmd     = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    invoker.canUndo shouldBe false
    invoker.execute(cmd)
    invoker.canUndo shouldBe true
    invoker.undo() shouldBe true
    invoker.getCurrentIndex shouldBe -1
    invoker.canRedo shouldBe true
    invoker.redo() shouldBe true
    invoker.getCurrentIndex shouldBe 0

  test("clear removes full history and resets index"):
    val invoker = new CommandInvoker()
    val cmd     = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    invoker.execute(cmd)
    invoker.clear()
    invoker.history.size shouldBe 0
    invoker.getCurrentIndex shouldBe -1

  test("execute after undo discards redo history"):
    val invoker = new CommandInvoker()
    val cmd1    = createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4))
    val cmd2    = createMoveCommand(sq(File.E, Rank.R7), sq(File.E, Rank.R5))
    val cmd3    = createMoveCommand(sq(File.D, Rank.R2), sq(File.D, Rank.R4))
    invoker.execute(cmd1)
    invoker.execute(cmd2)
    invoker.undo()
    invoker.getCurrentIndex shouldBe 0
    invoker.canRedo shouldBe true
    invoker.execute(cmd3)
    invoker.canRedo shouldBe false
    invoker.history.size shouldBe 2
    invoker.history.head shouldBe cmd1
    invoker.history(1) shouldBe cmd3
    invoker.getCurrentIndex shouldBe 1
