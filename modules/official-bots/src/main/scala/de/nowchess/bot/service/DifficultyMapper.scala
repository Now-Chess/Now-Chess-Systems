package de.nowchess.bot.service

import de.nowchess.bot.BotDifficulty

object DifficultyMapper:
  def fromElo(elo: Int): Option[BotDifficulty] =
    elo match
      case e if e >= 1000 && e <= 1400 => Some(BotDifficulty.Easy)
      case e if e >= 1401 && e <= 1800 => Some(BotDifficulty.Medium)
      case e if e >= 1801 && e <= 2300 => Some(BotDifficulty.Hard)
      case e if e >= 2301 && e <= 2800 => Some(BotDifficulty.Expert)
      case _                           => None
