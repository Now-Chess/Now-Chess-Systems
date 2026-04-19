package de.nowchess.api.game

import de.nowchess.api.bot.Bot
import de.nowchess.api.player.PlayerInfo

sealed trait Participant
final case class Human(playerInfo: PlayerInfo) extends Participant
final case class BotParticipant(bot: Bot)      extends Participant
