package de.nowchess.io.pgn

import de.nowchess.api.board.*
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.api.game.GameContext
import de.nowchess.api.io.GameContextExport
import de.nowchess.rules.sets.DefaultRules

object PgnExporter extends GameContextExport:

  /** Export a GameContext to PGN format. */
  def exportGameContext(context: GameContext): String =
    val headers = Map(
      "Event"  -> "?",
      "White"  -> "?",
      "Black"  -> "?",
      "Result" -> "*",
    )

    exportGame(headers, context.moves)

  /** Export a game with headers and moves to PGN format. */
  def exportGame(headers: Map[String, String], moves: List[Move]): String =
    val headerLines = headers
      .map { case (key, value) =>
        s"""[$key "$value"]"""
      }
      .mkString("\n")

    val moveText =
      if moves.isEmpty then ""
      else
        val contexts = moves.scanLeft(GameContext.initial)((ctx, move) => DefaultRules.applyMove(ctx)(move))
        val sanMoves = moves.zip(contexts).zip(contexts.tail).map { case ((move, ctxBefore), ctxAfter) =>
          moveToAlgebraic(move, ctxBefore, ctxAfter)
        }

        val groupedMoves = sanMoves.zipWithIndex.groupBy(_._2 / 2)
        val moveLines = for (moveNumber, movePairs) <- groupedMoves.toList.sortBy(_._1) yield
          val moveNum      = moveNumber + 1
          val whiteMoveStr = movePairs.find(_._2 % 2 == 0).map(_._1).getOrElse("")
          val blackMoveStr = movePairs.find(_._2 % 2 == 1).map(_._1).getOrElse("")
          if blackMoveStr.isEmpty then s"$moveNum. $whiteMoveStr"
          else s"$moveNum. $whiteMoveStr $blackMoveStr"

        val termination = headers.getOrElse("Result", "*")
        moveLines.mkString(" ") + s" $termination"

    if headerLines.isEmpty then moveText
    else if moveText.isEmpty then headerLines
    else s"$headerLines\n\n$moveText"

  private def disambiguate(from: Square, to: Square, pieceType: PieceType, ctx: GameContext): String =
    val competitors = DefaultRules
      .allLegalMoves(ctx)
      .filter(m => m.to == to && m.from != from && ctx.board.pieceAt(m.from).exists(_.pieceType == pieceType))
    if competitors.isEmpty then ""
    else
      val sameFile = competitors.exists(_.from.file == from.file)
      val sameRank = competitors.exists(_.from.rank == from.rank)
      if !sameFile then from.file.toString.toLowerCase
      else if !sameRank then (from.rank.ordinal + 1).toString
      else from.toString

  private def moveToAlgebraic(move: Move, ctxBefore: GameContext, ctxAfter: GameContext): String =
    val suffix =
      if DefaultRules.isCheckmate(ctxAfter) then "#"
      else if DefaultRules.isCheck(ctxAfter) then "+"
      else ""
    val base = move.moveType match
      case MoveType.CastleKingside  => "O-O"
      case MoveType.CastleQueenside => "O-O-O"
      case MoveType.EnPassant       => s"${move.from.file.toString.toLowerCase}x${move.to}"
      case MoveType.Promotion(pp) =>
        val promSuffix = pp match
          case PromotionPiece.Queen  => "=Q"
          case PromotionPiece.Rook   => "=R"
          case PromotionPiece.Bishop => "=B"
          case PromotionPiece.Knight => "=N"
        val isCapture = ctxBefore.board.pieceAt(move.to).isDefined
        if isCapture then s"${move.from.file.toString.toLowerCase}x${move.to}$promSuffix"
        else s"${move.to}$promSuffix"
      case MoveType.Normal(isCapture) =>
        val dest   = move.to.toString
        val capStr = if isCapture then "x" else ""
        ctxBefore.board.pieceAt(move.from).map(_.pieceType).getOrElse(PieceType.Pawn) match
          case PieceType.Pawn =>
            if isCapture then s"${move.from.file.toString.toLowerCase}x$dest"
            else dest
          case PieceType.Knight => s"N${disambiguate(move.from, move.to, PieceType.Knight, ctxBefore)}$capStr$dest"
          case PieceType.Bishop => s"B${disambiguate(move.from, move.to, PieceType.Bishop, ctxBefore)}$capStr$dest"
          case PieceType.Rook   => s"R${disambiguate(move.from, move.to, PieceType.Rook, ctxBefore)}$capStr$dest"
          case PieceType.Queen  => s"Q${disambiguate(move.from, move.to, PieceType.Queen, ctxBefore)}$capStr$dest"
          case PieceType.King   => s"K$capStr$dest"
    base + suffix
