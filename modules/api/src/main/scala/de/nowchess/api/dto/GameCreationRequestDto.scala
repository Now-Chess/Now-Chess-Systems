package de.nowchess.api.dto

import de.nowchess.api.game.GameMode

final case class GameCreationRequestDto(
    white: Option[PlayerInfoDto],
    black: Option[PlayerInfoDto],
    timeControl: Option[TimeControlDto],
    mode: Option[GameMode] = None,
)
