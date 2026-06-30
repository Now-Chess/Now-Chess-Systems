package de.nowchess.bot.bots.nnue

import de.nowchess.api.board.{Color, PieceType, Square}
import de.nowchess.api.game.GameContext

/** Endgame "mop-up" correction for the NNUE evaluation.
  *
  * Pure NNUE lacks explicit mating knowledge, so KX-vs-K conversions stall. When one side is reduced to a lone king and
  * the other holds sufficient mating material, this term rewards driving the bare king to the edge and walking the
  * winning king in. Returns a value from the side-to-move perspective (positive = good for side to move). Zero in any
  * position that is not a lone-king endgame, so middlegame NNUE output is untouched.
  */
object MopUp:

  private val EDGE_WEIGHT      = 10
  private val PROXIMITY_WEIGHT = 4
  private val MIN_WINNER_VALUE = 400

  def score(context: GameContext): Int =
    loneKingColor(context) match
      case None => 0
      case Some(loser) =>
        val winner = loser.opposite
        if winnerValue(context, winner) < MIN_WINNER_VALUE then 0
        else mopUp(context, winner, loser) * (if context.turn == winner then 1 else -1)

  private def mopUp(context: GameContext, winner: Color, loser: Color): Int =
    (for
      loserKing  <- context.kingSquare(loser)
      winnerKing <- context.kingSquare(winner)
    yield EDGE_WEIGHT * centerDistance(loserKing) +
      PROXIMITY_WEIGHT * (14 - kingDistance(winnerKing, loserKing))).getOrElse(0)

  private def loneKingColor(context: GameContext): Option[Color] =
    val nonKing = context.board.pieces.values.filter(_.pieceType != PieceType.King)
    val whiteHasOther = nonKing.exists(_.color == Color.White)
    val blackHasOther = nonKing.exists(_.color == Color.Black)
    if whiteHasOther == blackHasOther then None
    else if whiteHasOther then Some(Color.Black)
    else Some(Color.White)

  private def winnerValue(context: GameContext, winner: Color): Int =
    context.board.pieces.values.foldLeft(0) { (sum, piece) =>
      if piece.color != winner then sum
      else
        sum + (piece.pieceType match
          case PieceType.Queen  => 900
          case PieceType.Rook   => 500
          case PieceType.Bishop => 330
          case PieceType.Knight => 320
          case _                => 0)
    }

  private def centerDistance(sq: Square): Int =
    val fileDist = math.max(3 - sq.file.ordinal, sq.file.ordinal - 4)
    val rankDist = math.max(3 - sq.rank.ordinal, sq.rank.ordinal - 4)
    fileDist + rankDist

  private def kingDistance(a: Square, b: Square): Int =
    (a.file.ordinal - b.file.ordinal).abs + (a.rank.ordinal - b.rank.ordinal).abs
