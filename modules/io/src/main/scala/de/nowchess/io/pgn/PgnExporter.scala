package de.nowchess.io.pgn

import de.nowchess.api.board.*
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.api.game.GameContext
import de.nowchess.io.GameContextExport
import de.nowchess.rules.sets.DefaultRules

object PgnExporter extends GameContextExport:

  /** Export a GameContext to PGN format. */
  def exportGameContext(context: GameContext): String =
    val headers = Map(
      "Event" -> "?",
      "White" -> "?",
      "Black" -> "?",
      "Result" -> "*"
    )

    exportGame(headers, context.moves)

  /** Export a game with headers and moves to PGN format. */
  def exportGame(headers: Map[String, String], moves: List[Move]): String =
    val headerLines = headers.map { case (key, value) =>
      s"""[$key "$value"]"""
    }.mkString("\n")

    val moveText = if moves.isEmpty then ""
    else
      var ctx = GameContext.initial
      val sanMoves = moves.map { move =>
        val algebraic = moveToAlgebraic(move, ctx.board)
        ctx = DefaultRules.applyMove(ctx)(move)
        algebraic
      }

      val groupedMoves = sanMoves.zipWithIndex.groupBy(_._2 / 2)
      val moveLines = for (moveNumber, movePairs) <- groupedMoves.toList.sortBy(_._1) yield
        val moveNum = moveNumber + 1
        val whiteMoveStr = movePairs.find(_._2 % 2 == 0).map(_._1).getOrElse("")
        val blackMoveStr = movePairs.find(_._2 % 2 == 1).map(_._1).getOrElse("")
        if blackMoveStr.isEmpty then s"$moveNum. $whiteMoveStr"
        else s"$moveNum. $whiteMoveStr $blackMoveStr"

      val termination = headers.getOrElse("Result", "*")
      moveLines.mkString(" ") + s" $termination"

    if headerLines.isEmpty then moveText
    else if moveText.isEmpty then headerLines
    else s"$headerLines\n\n$moveText"

  /** Convert a Move to Standard Algebraic Notation using the board state before the move. */
  private def moveToAlgebraic(move: Move, boardBefore: Board): String =
    move.moveType match
      case MoveType.CastleKingside  => "O-O"
      case MoveType.CastleQueenside => "O-O-O"
      case MoveType.EnPassant       => s"${move.from.file.toString.toLowerCase}x${move.to}"
      case MoveType.Promotion(pp)   =>
        val promSuffix = pp match
          case PromotionPiece.Queen  => "=Q"
          case PromotionPiece.Rook   => "=R"
          case PromotionPiece.Bishop => "=B"
          case PromotionPiece.Knight => "=N"
        val isCapture = boardBefore.pieceAt(move.to).isDefined
        if isCapture then s"${move.from.file.toString.toLowerCase}x${move.to}$promSuffix"
        else s"${move.to}$promSuffix"
      case MoveType.Normal(isCapture) =>
        val dest   = move.to.toString
        val capStr = if isCapture then "x" else ""
        boardBefore.pieceAt(move.from).map(_.pieceType).getOrElse(PieceType.Pawn) match
          case PieceType.Pawn =>
            if isCapture then s"${move.from.file.toString.toLowerCase}x$dest"
            else dest
          case PieceType.Knight => s"N$capStr$dest"
          case PieceType.Bishop => s"B$capStr$dest"
          case PieceType.Rook   => s"R$capStr$dest"
          case PieceType.Queen  => s"Q$capStr$dest"
          case PieceType.King   => s"K$capStr$dest"


