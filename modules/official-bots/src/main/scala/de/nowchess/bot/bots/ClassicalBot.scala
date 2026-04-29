package de.nowchess.bot.bots

import de.nowchess.bot.Bot
import de.nowchess.api.game.GameContext
import de.nowchess.api.rules.RuleSet
import de.nowchess.bot.bots.classic.EvaluationClassic
import de.nowchess.bot.logic.AlphaBetaSearch
import de.nowchess.bot.util.PolyglotBook
import de.nowchess.bot.{BotDifficulty, BotMoveRepetition}
import de.nowchess.rules.sets.DefaultRules

object ClassicalBot:
  def apply(
      difficulty: BotDifficulty,
      rules: RuleSet = DefaultRules,
      book: Option[PolyglotBook] = None,
  ): Bot =
    val search       = AlphaBetaSearch(rules, weights = EvaluationClassic)
    val timeBudgetMs = 1000L
    context =>
      val blockedMoves = BotMoveRepetition.blockedMoves(context)
      book
        .flatMap(_.probe(context))
        .filterNot(blockedMoves.contains)
        .orElse(search.bestMoveWithTime(context, timeBudgetMs, blockedMoves))
