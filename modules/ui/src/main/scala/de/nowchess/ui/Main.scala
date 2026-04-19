package de.nowchess.ui

import de.nowchess.api.game.{BotParticipant, Human}
import de.nowchess.api.player.{PlayerId, PlayerInfo}
import de.nowchess.bot.util.PolyglotBook
import de.nowchess.bot.BotDifficulty
import de.nowchess.ui.terminal.TerminalUI
import de.nowchess.ui.gui.ChessGUILauncher

/** Application entry point - starts both GUI and Terminal UI for the chess game. Both views subscribe to the same
  * GameEngine via Observer pattern.
  */
object Main:
  def main(args: Array[String]): Unit =
    val book = PolyglotBook("../../modules/bot/codekiddy.bin")

    // Create the core game engine (single source of truth)
    val engine = new de.nowchess.chess.engine.GameEngine(
      participants = Map(
        de.nowchess.api.board.Color.White -> BotParticipant(
          de.nowchess.bot.bots.HybridBot(BotDifficulty.Easy, book = Some(book)),
        ),
        de.nowchess.api.board.Color.Black -> Human(PlayerInfo(PlayerId("p1"), "Player 1")),
      ),
    )

    engine.startGame()

    // Launch ScalaFX GUI in separate thread
    ChessGUILauncher.launch(engine)

    // Create and start the terminal UI (blocks on main thread)
    val tui = new TerminalUI(engine)
    tui.start()
