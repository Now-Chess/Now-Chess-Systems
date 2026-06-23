package de.nowchess.bot

import de.nowchess.bot.bots.{ClassicalBot, HybridBot}
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger

object BotController:
  private val log = Logger.getLogger(classOf[BotController])

  private val bots: Map[String, Bot] = Map(
    "easy"   -> ClassicalBot(BotDifficulty.Easy),
    "medium" -> ClassicalBot(BotDifficulty.Medium),
    "hard"   -> ClassicalBot(BotDifficulty.Hard),
    "expert" -> HybridBot(BotDifficulty.Expert, vetoReporter = log.debug(_)),
  )

  def getBot(name: String): Option[Bot] = bots.get(name.toLowerCase)
  def listBots: List[String]            = bots.keys.toList.sorted

@ApplicationScoped
class BotController:
  def getBot(name: String): Option[Bot] = BotController.getBot(name)
  def listBots: List[String]            = BotController.listBots
