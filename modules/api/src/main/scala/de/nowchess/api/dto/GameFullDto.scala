package de.nowchess.api.dto

final case class GameFullDto(
    gameId: String,
    white: PlayerInfoDto,
    black: PlayerInfoDto,
    state: GameStateDto,
)
