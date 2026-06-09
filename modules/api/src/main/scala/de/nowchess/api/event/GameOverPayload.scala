package de.nowchess.api.event

final case class GameOverPayload(
    gameId: String,
    result: String,
    terminationReason: String,
    whiteId: String,
    blackId: String,
)
