package de.nowchess.chess.client

case class GameRecordDto(
    gameId: String,
    fen: String,
    pgn: String,
    moveCount: Int,
    whiteId: String,
    whiteName: String,
    blackId: String,
    blackName: String,
    mode: String,
    resigned: Boolean,
    limitSeconds: java.lang.Integer,
    incrementSeconds: java.lang.Integer,
    daysPerMove: java.lang.Integer,
    whiteRemainingMs: java.lang.Long,
    blackRemainingMs: java.lang.Long,
    incrementMs: java.lang.Long,
    clockLastTickAt: java.lang.Long,
    clockMoveDeadline: java.lang.Long,
    clockActiveColor: String,
    pendingDrawOffer: String,
)
