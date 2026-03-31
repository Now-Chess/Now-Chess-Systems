package de.nowchess.chess.command

import de.nowchess.api.board.{Square, File, Rank, Board, Color}
import de.nowchess.chess.logic.GameHistory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable

class CommandInvokerThreadSafetyTest extends AnyFunSuite with Matchers:

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

  test("CommandInvoker is thread-safe for concurrent execute and history reads"):
    val invoker = new CommandInvoker()
    @volatile var raceDetected = false
    val exceptions = mutable.ListBuffer[Exception]()

    // Thread 1: executes commands
    val executorThread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          for i <- 1 to 1000 do
            val cmd = createMoveCommand(
              sq(File.E, Rank.R2),
              sq(File.E, Rank.R4)
            )
            invoker.execute(cmd)
        } catch {
          case e: Exception =>
            exceptions += e
            raceDetected = true
        }
      }
    })

    // Thread 2: reads history during execution
    val readerThread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          for _ <- 1 to 1000 do
            val _ = invoker.history
            val _ = invoker.getCurrentIndex
            Thread.sleep(0) // Yield to increase contention
        } catch {
          case e: Exception =>
            exceptions += e
            raceDetected = true
        }
      }
    })

    executorThread.start()
    readerThread.start()
    executorThread.join()
    readerThread.join()

    exceptions.isEmpty shouldBe true
    raceDetected shouldBe false

  test("CommandInvoker is thread-safe for concurrent execute, undo, and redo"):
    val invoker = new CommandInvoker()
    @volatile var raceDetected = false
    val exceptions = mutable.ListBuffer[Exception]()

    // Pre-populate with some commands
    for _ <- 1 to 5 do
      invoker.execute(createMoveCommand(sq(File.E, Rank.R2), sq(File.E, Rank.R4)))

    // Thread 1: executes new commands
    val executorThread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          for _ <- 1 to 500 do
            invoker.execute(createMoveCommand(sq(File.D, Rank.R2), sq(File.D, Rank.R4)))
        } catch {
          case e: Exception =>
            exceptions += e
            raceDetected = true
        }
      }
    })

    // Thread 2: undoes commands
    val undoThread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          for _ <- 1 to 500 do
            if invoker.canUndo then
              invoker.undo()
        } catch {
          case e: Exception =>
            exceptions += e
            raceDetected = true
        }
      }
    })

    // Thread 3: redoes commands
    val redoThread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          for _ <- 1 to 500 do
            if invoker.canRedo then
              invoker.redo()
        } catch {
          case e: Exception =>
            exceptions += e
            raceDetected = true
        }
      }
    })

    executorThread.start()
    undoThread.start()
    redoThread.start()
    executorThread.join()
    undoThread.join()
    redoThread.join()

    exceptions.isEmpty shouldBe true
    raceDetected shouldBe false
