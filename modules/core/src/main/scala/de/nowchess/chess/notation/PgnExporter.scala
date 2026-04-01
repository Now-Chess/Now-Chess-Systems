package de.nowchess.chess.notation

import de.nowchess.api.board.{PieceType, *}
import de.nowchess.api.move.PromotionPiece
import de.nowchess.chess.logic.{CastleSide, GameHistory, HistoryMove}

object PgnExporter:

  /** Export a game with headers and history to PGN format. */
  def exportGame(headers: Map[String, String], history: GameHistory): String =
    val headerLines = headers.map { case (key, value) =>
      s"""[$key "$value"]"""
    }.mkString("\n")

    val moveText = if history.moves.isEmpty then ""
    else
      val groupedMoves = history.moves.zipWithIndex.groupBy(_._2 / 2)
      val moveLines = for (moveNumber, movePairs) <- groupedMoves.toList.sortBy(_._1) yield
        val moveNum = moveNumber + 1
        val whiteMoveStr = movePairs.find(_._2 % 2 == 0).map(p => moveToAlgebraic(p._1)).getOrElse("")
        val blackMoveStr = movePairs.find(_._2 % 2 == 1).map(p => moveToAlgebraic(p._1)).getOrElse("")
        if blackMoveStr.isEmpty then s"$moveNum. $whiteMoveStr"
        else s"$moveNum. $whiteMoveStr $blackMoveStr"

      val termination = headers.getOrElse("Result", "*")
      moveLines.mkString(" ") + s" $termination"

    if headerLines.isEmpty then moveText
    else if moveText.isEmpty then headerLines
    else s"$headerLines\n\n$moveText"

  /** Convert a HistoryMove to Standard Algebraic Notation. */
  def moveToAlgebraic(move: HistoryMove): String =
    move.castleSide match
      case Some(CastleSide.Kingside)  => "O-O"
      case Some(CastleSide.Queenside) => "O-O-O"
      case None =>
        val dest       = move.to.toString
        val capStr     = if move.isCapture then "x" else ""
        val promSuffix = move.promotionPiece match
          case Some(PromotionPiece.Queen)  => "=Q"
          case Some(PromotionPiece.Rook)   => "=R"
          case Some(PromotionPiece.Bishop) => "=B"
          case Some(PromotionPiece.Knight) => "=N"
          case None                        => ""
        move.pieceType match
          case PieceType.Pawn =>
            if move.isCapture then s"${move.from.file.toString.toLowerCase}x$dest$promSuffix"
            else s"$dest$promSuffix"
          case PieceType.Knight => s"N$capStr$dest$promSuffix"
          case PieceType.Bishop => s"B$capStr$dest$promSuffix"
          case PieceType.Rook   => s"R$capStr$dest$promSuffix"
          case PieceType.Queen  => s"Q$capStr$dest$promSuffix"
          case PieceType.King   => s"K$capStr$dest$promSuffix"
