package de.nowchess.chess.controller

import scala.io.StdIn
import de.nowchess.api.board.{Board, Color, File, Piece, Rank, Square}
import de.nowchess.chess.logic.*
import de.nowchess.chess.view.Renderer

// ---------------------------------------------------------------------------
// Result ADT returned by the pure processMove function
// ---------------------------------------------------------------------------

sealed trait MoveResult
object MoveResult:
  case object Quit                                                                       extends MoveResult
  case class  InvalidFormat(raw: String)                                                extends MoveResult
  case object NoPiece                                                                   extends MoveResult
  case object WrongColor                                                                extends MoveResult
  case object IllegalMove                                                               extends MoveResult
  case class  Moved(newBoard: Board, newHistory: GameHistory, captured: Option[Piece], newTurn: Color)      extends MoveResult
  case class  MovedInCheck(newBoard: Board, newHistory: GameHistory, captured: Option[Piece], newTurn: Color) extends MoveResult
  case class  Checkmate(winner: Color)                                                  extends MoveResult
  case object Stalemate                                                                 extends MoveResult

// ---------------------------------------------------------------------------
// Controller
// ---------------------------------------------------------------------------

object GameController:

  /** Pure function: interprets one raw input line against the current game context.
   *  Has no I/O side effects — all output must be handled by the caller.
   */
  def processMove(board: Board, history: GameHistory, turn: Color, raw: String): MoveResult =
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
                if !MoveValidator.isLegal(board, history, from, to) then
                  MoveResult.IllegalMove
                else
                  val castleOpt = if MoveValidator.isCastle(board, from, to)
                                  then Some(MoveValidator.castleSide(from, to))
                                  else None
                  val (newBoard, captured) = castleOpt match
                    case Some(side) => (board.withCastle(turn, side), None)
                    case None       => board.withMove(from, to)
                  val newHistory = history.addMove(from, to, castleOpt)
                  GameRules.gameStatus(newBoard, newHistory, turn.opposite) match
                    case PositionStatus.Normal  => MoveResult.Moved(newBoard, newHistory, captured, turn.opposite)
                    case PositionStatus.InCheck => MoveResult.MovedInCheck(newBoard, newHistory, captured, turn.opposite)
                    case PositionStatus.Mated   => MoveResult.Checkmate(turn)
                    case PositionStatus.Drawn   => MoveResult.Stalemate

  /** Thin I/O shell: renders the board, reads a line, delegates to processMove,
   *  prints the outcome, and recurses until the game ends.
   */
  def gameLoop(board: Board, history: GameHistory, turn: Color): Unit =
    println()
    print(Renderer.render(board))
    println(s"${turn.label}'s turn. Enter move: ")
    val input = Option(StdIn.readLine()).getOrElse("quit").trim
    processMove(board, history, turn, input) match
      case MoveResult.Quit =>
        println("Game over. Goodbye!")
      case MoveResult.InvalidFormat(raw) =>
        println(s"Invalid move format '$raw'. Use coordinate notation, e.g. e2e4.")
        gameLoop(board, history, turn)
      case MoveResult.NoPiece =>
        println(s"No piece on ${Parser.parseMove(input).map(_._1).fold("?")(_.toString)}.")
        gameLoop(board, history, turn)
      case MoveResult.WrongColor =>
        println(s"That is not your piece.")
        gameLoop(board, history, turn)
      case MoveResult.IllegalMove =>
        println(s"Illegal move.")
        gameLoop(board, history, turn)
      case MoveResult.Moved(newBoard, newHistory, captured, newTurn) =>
        val prevTurn = newTurn.opposite
        captured.foreach: cap =>
          val toSq = Parser.parseMove(input).map(_._2).fold("?")(_.toString)
          println(s"${prevTurn.label} captures ${cap.color.label} ${cap.pieceType.label} on $toSq")
        gameLoop(newBoard, newHistory, newTurn)
      case MoveResult.MovedInCheck(newBoard, newHistory, captured, newTurn) =>
        val prevTurn = newTurn.opposite
        captured.foreach: cap =>
          val toSq = Parser.parseMove(input).map(_._2).fold("?")(_.toString)
          println(s"${prevTurn.label} captures ${cap.color.label} ${cap.pieceType.label} on $toSq")
        println(s"${newTurn.label} is in check!")
        gameLoop(newBoard, newHistory, newTurn)
      case MoveResult.Checkmate(winner) =>
        println(s"Checkmate! ${winner.label} wins.")
        gameLoop(Board.initial, GameHistory.empty, Color.White)
      case MoveResult.Stalemate =>
        println("Stalemate! The game is a draw.")
        gameLoop(Board.initial, GameHistory.empty, Color.White)
