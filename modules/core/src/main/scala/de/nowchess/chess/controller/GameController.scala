package de.nowchess.chess.controller

import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.api.move.PromotionPiece
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
  case class  PromotionRequired(
    from: Square,
    to: Square,
    boardBefore: Board,
    historyBefore: GameHistory,
    captured: Option[Piece],
    turn: Color
  ) extends MoveResult
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
      case "quit" | "q" => MoveResult.Quit
      case trimmed =>
        Parser.parseMove(trimmed) match
          case None              => MoveResult.InvalidFormat(trimmed)
          case Some((from, to))  => validateAndApply(board, history, turn, from, to)

  /** Apply a previously detected promotion move with the chosen piece.
   *  Called after processMove returned PromotionRequired.
   */
  def completePromotion(
    board: Board,
    history: GameHistory,
    from: Square,
    to: Square,
    piece: PromotionPiece,
    turn: Color
  ): MoveResult =
    val (boardAfterMove, captured) = board.withMove(from, to)
    val promotedPieceType = piece match
      case PromotionPiece.Queen  => PieceType.Queen
      case PromotionPiece.Rook   => PieceType.Rook
      case PromotionPiece.Bishop => PieceType.Bishop
      case PromotionPiece.Knight => PieceType.Knight
    val newBoard    = boardAfterMove.updated(to, Piece(turn, promotedPieceType))
    // Promotion is always a pawn move → clock resets
    val newHistory = history.addMove(from, to, None, Some(piece), wasPawnMove = true)
    toMoveResult(newBoard, newHistory, captured, turn)

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private def validateAndApply(board: Board, history: GameHistory, turn: Color, from: Square, to: Square): MoveResult =
    board.pieceAt(from) match
      case None                              => MoveResult.NoPiece
      case Some(piece) if piece.color != turn => MoveResult.WrongColor
      case Some(_) =>
        if !GameRules.legalMoves(board, history, turn).contains(from -> to) then MoveResult.IllegalMove
        else if MoveValidator.isPromotionMove(board, from, to) then
          MoveResult.PromotionRequired(from, to, board, history, board.pieceAt(to), turn)
        else applyNormalMove(board, history, turn, from, to)

  private def applyNormalMove(board: Board, history: GameHistory, turn: Color, from: Square, to: Square): MoveResult =
    val castleOpt              = Option.when(MoveValidator.isCastle(board, from, to))(MoveValidator.castleSide(from, to))
    val isEP                   = EnPassantCalculator.isEnPassant(board, history, from, to)
    val (newBoard, captured)   = castleOpt match
      case Some(side) => (board.withCastle(turn, side), None)
      case None =>
        val (b, cap) = board.withMove(from, to)
        if isEP then
          val capturedSq = EnPassantCalculator.capturedPawnSquare(to, turn)
          (b.removed(capturedSq), board.pieceAt(capturedSq))
        else (b, cap)
    val wasPawnMove = board.pieceAt(from).exists(_.pieceType == PieceType.Pawn)
    val wasCapture  = captured.isDefined
    val newHistory  = history.addMove(from, to, castleOpt, wasPawnMove = wasPawnMove, wasCapture = wasCapture)
    toMoveResult(newBoard, newHistory, captured, turn)

  private def toMoveResult(newBoard: Board, newHistory: GameHistory, captured: Option[Piece], turn: Color): MoveResult =
    GameRules.gameStatus(newBoard, newHistory, turn.opposite) match
      case PositionStatus.Normal  => MoveResult.Moved(newBoard, newHistory, captured, turn.opposite)
      case PositionStatus.InCheck => MoveResult.MovedInCheck(newBoard, newHistory, captured, turn.opposite)
      case PositionStatus.Mated   => MoveResult.Checkmate(turn)
      case PositionStatus.Drawn   => MoveResult.Stalemate
