package de.nowchess.ui

import de.nowchess.chess.engine.GameEngine
import de.nowchess.ui.terminal.TerminalUI

/** Application entry point - starts the Terminal UI for the chess game. */
object Main:
  def main(args: Array[String]): Unit =
    // Create the core game engine (single source of truth)
    val engine = new GameEngine()

    // Create and start the terminal UI
    val tui = new TerminalUI(engine)
    tui.start()

