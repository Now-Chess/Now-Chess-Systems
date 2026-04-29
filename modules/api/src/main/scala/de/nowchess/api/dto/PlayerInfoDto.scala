package de.nowchess.api.dto

import de.nowchess.api.player.PlayerType

final case class PlayerInfoDto(id: String, displayName: String, playerType: PlayerType)
