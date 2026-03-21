package de.nowchess.chess

import scala.io.StdIn

@main def chessMain(): Unit =
  println("NowChess TUI — type moves in coordinate notation (e.g. e2e4). Type 'quit' to exit.")
  gameLoop(Board.initial, Color.White)

private def gameLoop(board: Board, turn: Color): Unit =
  println()
  print(Renderer.render(board))
  println(s"${turn.label}'s turn. Enter move: ")
  val input = Option(StdIn.readLine()).getOrElse("quit").trim
  input match
    case "quit" | "q" =>
      println("Game over. Goodbye!")
    case raw =>
      Parser.parseMove(raw) match
        case None =>
          println(s"Invalid move format '$raw'. Use coordinate notation, e.g. e2e4.")
          gameLoop(board, turn)
        case Some((from, to)) =>
          board.pieceAt(from) match
            case None =>
              println(s"No piece on ${from.label}.")
              gameLoop(board, turn)
            case Some(movingPiece) =>
              val (newBoard, captured) = board.withMove(from, to)
              captured.foreach: cap =>
                println(s"${turn.label} captures ${cap.color.label} ${cap.pieceType.label} on ${to.label}")
              gameLoop(newBoard, turn.opposite)
