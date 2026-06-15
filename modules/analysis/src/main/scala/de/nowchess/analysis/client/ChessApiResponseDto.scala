package de.nowchess.analysis.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** Response from chess-api.com v1 analysis endpoint.
  *
  * The API returns a JSON object. Fields not listed here are ignored.
  */
@JsonIgnoreProperties(ignoreUnknown = true)
case class ChessApiResponseDto(
    /** Best move in UCI format (e.g. "e2e4"). */
    move: Option[String] = None,
    /** Centipawn evaluation (from white's perspective). */
    centipawns: Option[Double] = None,
    /** Mate-in-N (positive = white wins, negative = black wins). */
    mate: Option[Int] = None,
    /** Principal variation: space-separated UCI moves. */
    pv: Option[String] = None,
    /** Actual depth searched. */
    depth: Option[Int] = None,
    /** Text description of the position/move quality. */
    text: Option[String] = None,
)
