package de.nowchess.ui.terminal

import java.util.concurrent.atomic.AtomicBoolean
import scala.io.StdIn
import de.nowchess.api.game.DrawReason
import de.nowchess.chess.engine.GameEngine
import de.nowchess.chess.observer.*
import de.nowchess.ui.utils.Renderer

/** Terminal UI that implements Observer pattern. Subscribes to GameEngine and receives state change events. Handles all
  * I/O and user interaction in the terminal.
  */
class TerminalUI(engine: GameEngine) extends Observer:
  private val running = new AtomicBoolean(true)

  /** Called by GameEngine whenever a game event occurs. */
  override def onGameEvent(event: GameEvent): Unit =
    event match
      case e: MoveExecutedEvent =>
        println()
        print(Renderer.render(e.context.board))
        e.capturedPiece.foreach: cap =>
          println(s"Captured: $cap on ${e.toSquare}")
        printPrompt(e.context.turn)

      case e: MoveUndoneEvent =>
        println(s"Undo: ${e.pgnNotation}")
        println()
        print(Renderer.render(e.context.board))
        printPrompt(e.context.turn)

      case e: MoveRedoneEvent =>
        println(s"Redo: ${e.pgnNotation}")
        println()
        print(Renderer.render(e.context.board))
        printPrompt(e.context.turn)

      case e: CheckDetectedEvent =>
        println(s"${e.context.turn.label} is in check!")

      case e: CheckmateEvent =>
        println(s"Checkmate! ${e.winner.label} wins.")
        println()
        print(Renderer.render(e.context.board))

      case e: DrawEvent =>
        val msg = e.reason match
          case DrawReason.Stalemate            => "Stalemate! The game is a draw."
          case DrawReason.InsufficientMaterial => "Draw by insufficient material."
          case DrawReason.FiftyMoveRule        => "Draw claimed under the 50-move rule."
          case DrawReason.ThreefoldRepetition  => "Draw by threefold repetition."
          case DrawReason.Agreement            => "Draw by agreement."
        println(msg)
        println()
        print(Renderer.render(e.context.board))

      case e: InvalidMoveEvent =>
        println(s"⚠️  ${e.reason}")

      case e: BoardResetEvent =>
        println("Board has been reset to initial position.")
        println()
        print(Renderer.render(e.context.board))
        printPrompt(e.context.turn)

      case _: FiftyMoveRuleAvailableEvent =>
        println("50-move rule is now available — type 'draw' to claim.")

      case _: ThreefoldRepetitionAvailableEvent =>
        println("Threefold repetition is now available — type 'draw' to claim.")

      case e: PgnLoadedEvent =>
        println("PGN loaded successfully.")
        println()
        print(Renderer.render(e.context.board))
        printPrompt(e.context.turn)

  /** Start the terminal UI game loop. */
  def start(): Unit =
    // Register as observer
    engine.subscribe(this)

    // Show initial board
    println()
    print(Renderer.render(engine.board))
    printPrompt(engine.turn)

    while running.get() do
      val input = Option(StdIn.readLine()).getOrElse("quit").trim
      synchronized {
        input.toLowerCase match
          case "quit" | "q" =>
            running.set(false)
            println("Game over. Goodbye!")
          case "" =>
            printPrompt(engine.turn)
          case _ =>
            engine.processUserInput(input)
      }

    // Unsubscribe when done
    engine.unsubscribe(this)

  private def printPrompt(turn: de.nowchess.api.board.Color): Unit =
    val undoHint = if engine.canUndo then " [undo]" else ""
    val redoHint = if engine.canRedo then " [redo]" else ""
    print(s"${turn.label}'s turn. Enter move (or 'quit'/'q' to exit)$undoHint$redoHint: ")
