package de.nowchess.analysis.client

/** Request body sent to chess-api.com v1 `/` endpoint. */
case class ChessApiRequestDto(fen: String, depth: Int)
