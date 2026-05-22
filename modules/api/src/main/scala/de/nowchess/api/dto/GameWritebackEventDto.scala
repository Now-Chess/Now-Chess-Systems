package de.nowchess.api.dto

import com.fasterxml.jackson.databind.annotation.JsonDeserialize

case class GameWritebackEventDto(
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
    limitSeconds: Option[Int],
    incrementSeconds: Option[Int],
    daysPerMove: Option[Int],
    @JsonDeserialize(contentAs = classOf[java.lang.Long]) whiteRemainingMs: Option[Long],
    @JsonDeserialize(contentAs = classOf[java.lang.Long]) blackRemainingMs: Option[Long],
    @JsonDeserialize(contentAs = classOf[java.lang.Long]) incrementMs: Option[Long],
    @JsonDeserialize(contentAs = classOf[java.lang.Long]) clockLastTickAt: Option[Long],
    @JsonDeserialize(contentAs = classOf[java.lang.Long]) clockMoveDeadline: Option[Long],
    clockActiveColor: Option[String],
    pendingDrawOffer: Option[String],
    result: Option[String] = None,
    terminationReason: Option[String] = None,
    redoStack: List[String] = Nil,
    pendingTakebackRequest: Option[String] = None,
)
