package de.nowchess.analysis.error

sealed class AnalysisException(val status: Int, val code: String, message: String) extends RuntimeException(message)

class InvalidFenException(fen: String) extends AnalysisException(400, "INVALID_FEN", s"Invalid FEN string: $fen")

class AnalysisUpstreamException(cause: Throwable)
    extends AnalysisException(502, "UPSTREAM_ERROR", s"Chess API unavailable: ${cause.getMessage}")
