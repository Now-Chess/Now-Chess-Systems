package de.nowchess.bot

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move

type Bot = GameContext => Option[Move]
