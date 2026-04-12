package de.nowchess.ui

import de.nowchess.chess.engine.GameEngine
import de.nowchess.ui.terminal.TerminalUI
import de.nowchess.ui.gui.ChessGUILauncher

/** Application entry point - starts both GUI and Terminal UI for the chess game. Both views subscribe to the same
  * GameEngine via Observer pattern.
  */
object Main:
  def main(args: Array[String]): Unit =
    // Create the core game engine (single source of truth)
    val engine = new GameEngine()

    // Launch ScalaFX GUI in separate thread
    ChessGUILauncher.launch(engine)

    // Create and start the terminal UI (blocks on main thread)
    val tui = new TerminalUI(engine)
    tui.start()
