package de.nowchess.ui.terminal

import scala.io.StdIn
import de.nowchess.chess.engine.GameEngine
import de.nowchess.chess.observer.{Observer, GameEvent, *}
import de.nowchess.chess.view.Renderer

/** Terminal UI that implements Observer pattern.
 *  Subscribes to GameEngine and receives state change events.
 *  Handles all I/O and user interaction in the terminal.
 */
class TerminalUI(engine: GameEngine) extends Observer:
  private var running = true

  /** Called by GameEngine whenever a game event occurs. */
  override def onGameEvent(event: GameEvent): Unit =
    event match
      case e: MoveExecutedEvent =>
        println()
        print(Renderer.render(e.board))
        e.capturedPiece.foreach: cap =>
          println(s"Captured: $cap on ${e.toSquare}")
        printPrompt(e.turn)

      case e: CheckDetectedEvent =>
        println(s"${e.turn.label} is in check!")

      case e: CheckmateEvent =>
        println(s"Checkmate! ${e.winner.label} wins.")
        println()
        print(Renderer.render(e.board))

      case e: StalemateEvent =>
        println("Stalemate! The game is a draw.")
        println()
        print(Renderer.render(e.board))

      case e: InvalidMoveEvent =>
        println(s"⚠️  ${e.reason}")

      case e: BoardResetEvent =>
        println("Board has been reset to initial position.")
        println()
        print(Renderer.render(e.board))
        printPrompt(e.turn)

  /** Start the terminal UI game loop. */
  def start(): Unit =
    // Register as observer
    engine.subscribe(this)

    // Show initial board
    println()
    print(Renderer.render(engine.board))
    printPrompt(engine.turn)

    // Game loop
    while running do
      val input = Option(StdIn.readLine()).getOrElse("quit").trim
      input.toLowerCase match
        case "quit" | "q" =>
          running = false
          println("Game over. Goodbye!")
        case "" =>
          printPrompt(engine.turn)
        case _ =>
          engine.processUserInput(input)

    // Unsubscribe when done
    engine.unsubscribe(this)

  private def printPrompt(turn: de.nowchess.api.board.Color): Unit =
    val undoHint = if engine.canUndo then " [undo]" else ""
    val redoHint = if engine.canRedo then " [redo]" else ""
    print(s"${turn.label}'s turn. Enter move (or 'quit'/'q' to exit)$undoHint$redoHint: ")

