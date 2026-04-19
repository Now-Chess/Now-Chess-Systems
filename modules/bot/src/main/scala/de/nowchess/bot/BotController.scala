package de.nowchess.bot

import de.nowchess.api.bot.Bot
import de.nowchess.bot.bots.ClassicalBot

object BotController {

  private val bots: Map[String, Bot] = Map(
    "easy"   -> ClassicalBot(BotDifficulty.Easy),
    "medium" -> ClassicalBot(BotDifficulty.Medium),
    "hard"   -> ClassicalBot(BotDifficulty.Hard),
    "expert" -> ClassicalBot(BotDifficulty.Expert),
  )

  /** Get a bot by name. */
  def getBot(name: String): Option[Bot] = bots.get(name.toLowerCase)

  /** List all available bot names. */
  def listBots: List[String] = bots.keys.toList.sorted

}
