package de.nowchess.api.dto

final case class CreateGameRequestDto(
    white: Option[PlayerInfoDto],
    black: Option[PlayerInfoDto],
)
