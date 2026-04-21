package de.nowchess.api.dto

final case class LegalMoveDto(
    from: String,
    to: String,
    uci: String,
    moveType: String,
    promotion: Option[String],
)
