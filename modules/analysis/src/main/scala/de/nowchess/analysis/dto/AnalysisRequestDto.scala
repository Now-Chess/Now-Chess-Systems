package de.nowchess.analysis.dto

/** Request body for the analysis endpoint.
  *
  * @param fen
  *   FEN string representing the position to analyse.
  * @param depth
  *   Engine search depth (1-99). Defaults to 12 when absent.
  */
case class AnalysisRequestDto(fen: String, depth: Option[Int] = None)
