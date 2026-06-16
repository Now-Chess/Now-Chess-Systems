package de.nowchess.analysis.dto

/** Response from the analysis endpoint.
  *
  * @param fen
  *   The analysed FEN.
  * @param depth
  *   The search depth used.
  * @param bestMove
  *   Best move in UCI notation (e.g. "e2e4"), or None if not available.
  * @param evaluation
  *   Centipawn evaluation from white's perspective, or None.
  * @param mate
  *   Mate-in-N value (positive = white wins, negative = black wins), or None.
  * @param continuationMoves
  *   Principal variation as list of UCI moves.
  */
case class AnalysisResponseDto(
    fen: String,
    depth: Int,
    bestMove: Option[String],
    evaluation: Option[Double],
    mate: Option[Int],
    continuationMoves: List[String],
)
