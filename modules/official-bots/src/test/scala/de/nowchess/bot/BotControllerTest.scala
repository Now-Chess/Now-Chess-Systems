package de.nowchess.bot

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BotControllerTest extends AnyFunSuite with Matchers:

  test("BotController can be instantiated"):
    BotController.listBots should not be empty

  test("getBot returns known bots by name"):
    BotController.getBot("easy") should not be None
    BotController.getBot("medium") should not be None
    BotController.getBot("hard") should not be None
    BotController.getBot("expert") should not be None

  test("getBot returns None for unknown bot"):
    BotController.getBot("unknown") should be(None)
