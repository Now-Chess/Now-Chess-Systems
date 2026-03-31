package de.nowchess.chess.controller

import de.nowchess.api.board.{Board, Color, File, Piece, Rank, Square}
import de.nowchess.chess.logic.*

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
                  val isEP = EnPassantCalculator.isEnPassant(board, history, from, to)
                  val (newBoard, captured) = castleOpt match
                    case Some(side) => (board.withCastle(turn, side), None)
                    case None =>
                      val (b, cap) = board.withMove(from, to)
                      if isEP then
                        val capturedSq = EnPassantCalculator.capturedPawnSquare(to, turn)
                        (b.removed(capturedSq), board.pieceAt(capturedSq))
                      else (b, cap)
                  val newHistory = history.addMove(from, to, castleOpt)
                  GameRules.gameStatus(newBoard, newHistory, turn.opposite) match
                    case PositionStatus.Normal  => MoveResult.Moved(newBoard, newHistory, captured, turn.opposite)
                    case PositionStatus.InCheck => MoveResult.MovedInCheck(newBoard, newHistory, captured, turn.opposite)
                    case PositionStatus.Mated   => MoveResult.Checkmate(turn)
                    case PositionStatus.Drawn   => MoveResult.Stalemate
