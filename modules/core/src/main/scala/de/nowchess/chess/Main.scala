package de.nowchess.chess

import de.nowchess.api.board.{Board, Color}
import de.nowchess.chess.controller.GameController

@main def chessMain(): Unit =
  println("NowChess TUI — type moves in coordinate notation (e.g. e2e4). Type 'quit' to exit.")
  GameController.gameLoop(Board.initial, Color.White)
