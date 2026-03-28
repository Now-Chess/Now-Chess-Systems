package de.nowchess.chess

import de.nowchess.api.board.Board
import de.nowchess.api.board.Color
import de.nowchess.chess.controller.GameController
import de.nowchess.chess.logic.GameHistory

object Main {
  def main(args: Array[String]): Unit =
    println("NowChess TUI — type moves in coordinate notation (e.g. e2e4). Type 'quit' to exit.")
    GameController.gameLoop(Board.initial, GameHistory.empty, Color.White)
}
