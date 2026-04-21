package de.nowchess.api.dto

final case class GameFullEventDto(`type`: String, game: GameFullDto)

object GameFullEventDto:
  def apply(game: GameFullDto): GameFullEventDto = GameFullEventDto("gameFull", game)
