package de.nowchess.bot.logic

import de.nowchess.api.board.{Board, Color, Piece, PieceType, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}

import scala.annotation.tailrec
import scala.collection.mutable

object MoveOrdering:

  class OrderingContext:
    private val killerMoves  = mutable.Map[Int, List[Move]]()
    private val historyTable = mutable.Map[(Int, Int), Int]()

    def addKillerMove(ply: Int, move: Move): Unit =
      val current = killerMoves.getOrElse(ply, List())
      if current.isEmpty || (current.head.from != move.from || current.head.to != move.to) then
        killerMoves(ply) = (move :: current).take(2)

    def getKillerMoves(ply: Int): List[Move] =
      killerMoves.getOrElse(ply, List())

    def addHistory(from: Int, to: Int, bonus: Int): Unit =
      val key = (from, to)
      historyTable(key) = historyTable.getOrElse(key, 0) + bonus

    def getHistory(from: Int, to: Int): Int =
      historyTable.getOrElse((from, to), 0)

    def clear(): Unit =
      killerMoves.clear()
      historyTable.clear()

  def score(
      context: GameContext,
      move: Move,
      ttBestMove: Option[Move],
      ply: Int = 0,
      ordering: OrderingContext = new OrderingContext(),
  ): Int =
    if ttBestMove.exists(m => m.from == move.from && m.to == move.to) then Int.MaxValue
    else
      move.moveType match
        case MoveType.Promotion(PromotionPiece.Queen) =>
          1_000_000 + promotionCaptureBonus(context, move)
        case MoveType.Normal(true) | MoveType.EnPassant =>
          captureScore(context, move)
        case MoveType.Promotion(_) =>
          50_000 + promotionCaptureBonus(context, move)
        case _ => scoreQuietMove(move, ply, ordering)

  def sort(
      context: GameContext,
      moves: List[Move],
      ttBestMove: Option[Move],
      ply: Int = 0,
      ordering: OrderingContext = new OrderingContext(),
  ): List[Move] =
    moves.sortBy(m => -score(context, m, ttBestMove, ply, ordering))

  private def scoreQuietMove(move: Move, ply: Int, ordering: OrderingContext): Int =
    val isKiller = ordering.getKillerMoves(ply).exists(k => k.from == move.from && k.to == move.to)
    val fromIdx  = move.from.rank.ordinal * 8 + move.from.file.ordinal
    val toIdx    = move.to.rank.ordinal * 8 + move.to.file.ordinal
    val history  = ordering.getHistory(fromIdx, toIdx)
    if isKiller then 10_000 + (history / 10) else history / 10

  private def promotionCaptureBonus(context: GameContext, move: Move): Int =
    if isCapture(context, move) then captureScore(context, move) else 0

  private def captureScore(context: GameContext, move: Move): Int =
    val see     = staticExchange(context, move)
    val seeBias = if see >= 0 then 20_000 else -20_000
    100_000 + mvvLva(context, move) + seeBias + see

  private def mvvLva(context: GameContext, move: Move): Int =
    (victimValue(context, move) * 10) - attackerValue(context, move)

  private def attackerValue(context: GameContext, move: Move): Int =
    context.board.pieceAt(move.from).map(pieceValue).getOrElse(0)

  private def victimValue(context: GameContext, move: Move): Int =
    move.moveType match
      case MoveType.Normal(true) => context.board.pieceAt(move.to).map(pieceValue).getOrElse(0)
      case MoveType.EnPassant    => 1
      case MoveType.Promotion(_) => context.board.pieceAt(move.to).map(pieceValue).getOrElse(0)
      case _                     => 0

  private def pieceValue(piece: Piece): Int = piece.pieceType match
    case PieceType.Pawn   => 1
    case PieceType.Knight => 3
    case PieceType.Bishop => 3
    case PieceType.Rook   => 5
    case PieceType.Queen  => 9
    case PieceType.King   => 200

  private def isCapture(context: GameContext, move: Move): Boolean = move.moveType match
    case MoveType.Normal(true) => true
    case MoveType.EnPassant    => true
    case MoveType.Promotion(_) => context.board.pieceAt(move.to).exists(_.color != context.turn)
    case _                     => false

  private def staticExchange(context: GameContext, move: Move): Int =
    if !isCapture(context, move) then 0
    else
      val target      = move.to
      val initialGain = victimValue(context, move)
      movedPieceAfterMove(context, move).fold(initialGain) { moved =>
        val boardAfterMove = applySeeMove(context.board, move, moved)
        initialGain - seeGain(boardAfterMove, target, context.turn.opposite, pieceValue(moved))
      }

  private def movedPieceAfterMove(context: GameContext, move: Move): Option[Piece] =
    move.moveType match
      case MoveType.Promotion(pp) => Some(Piece(context.turn, promotionPieceType(pp)))
      case _                      => context.board.pieceAt(move.from)

  private def seeGain(board: Board, target: Square, side: Color, currentValue: Int): Int =
    leastValuableAttacker(board, target, side) match
      case None => 0
      case Some((from, attacker)) =>
        val nextBoard = board.removed(from).updated(target, attacker)
        val replyGain = seeGain(nextBoard, target, side.opposite, pieceValue(attacker))
        math.max(0, currentValue - replyGain)

  private def applySeeMove(board: Board, move: Move, moved: Piece): Board =
    move.moveType match
      case MoveType.EnPassant =>
        val capturedSquare = Square(move.to.file, move.from.rank)
        board.removed(move.from).removed(capturedSquare).updated(move.to, moved)
      case _ => board.removed(move.from).updated(move.to, moved)

  private def leastValuableAttacker(board: Board, target: Square, color: Color): Option[(Square, Piece)] =
    board.pieces
      .collect {
        case (sq, piece) if piece.color == color && attacksSquare(board, sq, target, piece) => (sq, piece)
      }
      .toList
      .sortBy { case (_, piece) => pieceValue(piece) }
      .headOption

  private def attacksSquare(board: Board, from: Square, target: Square, piece: Piece): Boolean =
    val df = target.file.ordinal - from.file.ordinal
    val dr = target.rank.ordinal - from.rank.ordinal
    piece.pieceType match
      case PieceType.Pawn =>
        val dir = if piece.color == Color.White then 1 else -1
        dr == dir && math.abs(df) == 1
      case PieceType.Knight =>
        val adf = math.abs(df)
        val adr = math.abs(dr)
        (adf == 1 && adr == 2) || (adf == 2 && adr == 1)
      case PieceType.Bishop => clearLine(board, from, target, df, dr, diagonal = true)
      case PieceType.Rook   => clearLine(board, from, target, df, dr, diagonal = false)
      case PieceType.Queen =>
        clearLine(board, from, target, df, dr, diagonal = true) ||
        clearLine(board, from, target, df, dr, diagonal = false)
      case PieceType.King => math.abs(df) <= 1 && math.abs(dr) <= 1

  private def clearLine(board: Board, from: Square, target: Square, df: Int, dr: Int, diagonal: Boolean): Boolean =
    val valid =
      if diagonal then math.abs(df) == math.abs(dr) && df != 0 else (df == 0 && dr != 0) || (dr == 0 && df != 0)
    valid && pathClear(board, from, target, Integer.compare(df, 0), Integer.compare(dr, 0))

  @tailrec
  private def pathClear(board: Board, from: Square, target: Square, stepF: Int, stepR: Int): Boolean =
    from.offset(stepF, stepR) match
      case None                         => false
      case Some(next) if next == target => true
      case Some(next)                   => board.pieceAt(next).isEmpty && pathClear(board, next, target, stepF, stepR)

  private def promotionPieceType(piece: PromotionPiece): PieceType = piece match
    case PromotionPiece.Knight => PieceType.Knight
    case PromotionPiece.Bishop => PieceType.Bishop
    case PromotionPiece.Rook   => PieceType.Rook
    case PromotionPiece.Queen  => PieceType.Queen
