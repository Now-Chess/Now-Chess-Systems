package de.nowchess.api.dto

final case class GameStateDto(
    fen: String,
    pgn: String,
    turn: String,
    status: String,
    winner: Option[String],
    moves: List[String],
    undoAvailable: Boolean,
    redoAvailable: Boolean,
    clock: Option[ClockDto],
    takebackRequestedBy: Option[String] = None,
)
