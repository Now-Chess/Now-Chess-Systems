package de.nowchess.bot

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BotDifficultyTest extends AnyFunSuite with Matchers:

  test("all difficulty values are defined"):
    val difficulties = BotDifficulty.values
    difficulties should contain(BotDifficulty.Easy)
    difficulties should contain(BotDifficulty.Medium)
    difficulties should contain(BotDifficulty.Hard)
    difficulties should contain(BotDifficulty.Expert)
    difficulties should have length 4
