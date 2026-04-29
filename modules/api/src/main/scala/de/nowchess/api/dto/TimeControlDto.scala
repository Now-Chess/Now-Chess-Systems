package de.nowchess.api.dto

final case class TimeControlDto(
    limitSeconds: Option[Int],
    incrementSeconds: Option[Int],
    daysPerMove: Option[Int],
)
