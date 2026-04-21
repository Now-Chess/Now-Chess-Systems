package de.nowchess.api.dto

final case class GameStateEventDto(`type`: String, state: GameStateDto)

object GameStateEventDto:
  def apply(state: GameStateDto): GameStateEventDto = GameStateEventDto("gameState", state)
