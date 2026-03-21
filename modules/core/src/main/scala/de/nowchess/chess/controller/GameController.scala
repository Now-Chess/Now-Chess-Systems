package de.nowchess.chess.controller

import scala.io.StdIn
import de.nowchess.api.board.{Board, Color, Piece}
import de.nowchess.chess.logic.MoveValidator
import de.nowchess.chess.view.Renderer

// ---------------------------------------------------------------------------
// Result ADT returned by the pure processMove function
// ---------------------------------------------------------------------------

sealed trait MoveResult
object MoveResult:
  case object Quit                                                              extends MoveResult
  case class  InvalidFormat(raw: String)                                        extends MoveResult
  case object NoPiece                                                           extends MoveResult
  case object WrongColor                                                        extends MoveResult
  case object IllegalMove                                                       extends MoveResult
  case class  Moved(newBoard: Board, captured: Option[Piece], newTurn: Color)  extends MoveResult

// ---------------------------------------------------------------------------
// Controller
// ---------------------------------------------------------------------------

object GameController:

  /** Pure function: interprets one raw input line against the current board state.
   *  Has no I/O side effects — all output must be handled by the caller.
   */
  def processMove(board: Board, turn: Color, raw: String): MoveResult =
    raw.trim match
      case "quit" | "q" =>
        MoveResult.Quit
      case trimmed =>
        Parser.parseMove(trimmed) match
          case None =>
            MoveResult.InvalidFormat(trimmed)
          case Some((from, to)) =>
            board.pieceAt(from) match
              case None =>
                MoveResult.NoPiece
              case Some(piece) if piece.color != turn =>
                MoveResult.WrongColor
              case Some(_) =>
                if !MoveValidator.isLegal(board, from, to) then
                  MoveResult.IllegalMove
                else
                  val (newBoard, captured) = board.withMove(from, to)
                  MoveResult.Moved(newBoard, captured, turn.opposite)

  /** Thin I/O shell: renders the board, reads a line, delegates to processMove,
   *  prints the outcome, and recurses until the game ends.
   *  Behaviour is identical to the original implementation.
   */
  def gameLoop(board: Board, turn: Color): Unit =
    println()
    print(Renderer.render(board))
    println(s"${turn.label}'s turn. Enter move: ")
    val input = Option(StdIn.readLine()).getOrElse("quit").trim
    processMove(board, turn, input) match
      case MoveResult.Quit =>
        println("Game over. Goodbye!")
      case MoveResult.InvalidFormat(raw) =>
        println(s"Invalid move format '$raw'. Use coordinate notation, e.g. e2e4.")
        gameLoop(board, turn)
      case MoveResult.NoPiece =>
        println(s"No piece on ${Parser.parseMove(input).map(_._1).fold("?")(_.toString)}.")
        gameLoop(board, turn)
      case MoveResult.WrongColor =>
        println(s"That is not your piece.")
        gameLoop(board, turn)
      case MoveResult.IllegalMove =>
        println(s"Illegal move.")
        gameLoop(board, turn)
      case MoveResult.Moved(newBoard, captured, newTurn) =>
        val prevTurn = newTurn.opposite
        captured.foreach: cap =>
          val toSq = Parser.parseMove(input).map(_._2).fold("?")(_.toString)
          println(s"${prevTurn.label} captures ${cap.color.label} ${cap.pieceType.label} on $toSq")
        gameLoop(newBoard, newTurn)
