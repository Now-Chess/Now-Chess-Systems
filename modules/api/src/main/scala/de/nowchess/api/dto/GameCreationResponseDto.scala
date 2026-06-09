package de.nowchess.api.dto

final case class GameCreationResponseDto(
    gameId: Option[String],
    error: Option[String] = None,
)
