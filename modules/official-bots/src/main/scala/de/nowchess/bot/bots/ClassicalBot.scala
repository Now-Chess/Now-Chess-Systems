package de.nowchess.bot.bots

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.api.rules.RuleSet
import de.nowchess.bot.bots.classic.EvaluationClassic
import de.nowchess.bot.logic.AlphaBetaSearch
import de.nowchess.bot.util.PolyglotBook
import de.nowchess.bot.{Bot, BotDifficulty, BotMoveRepetition, TimeControl}
import de.nowchess.rules.sets.DefaultRules

object ClassicalBot:
  private val defaultBudgetMs = 1000L

  def apply(
      difficulty: BotDifficulty,
      rules: RuleSet = DefaultRules,
      book: Option[PolyglotBook] = None,
  ): Bot =
    val search = AlphaBetaSearch(rules, weights = EvaluationClassic)
    new Bot:
      def move(context: GameContext, time: TimeControl): Option[Move] =
        val budget       = if time.isClocked then time.budgetMs else defaultBudgetMs
        val blockedMoves = BotMoveRepetition.blockedMoves(context)
        book
          .flatMap(_.probe(context))
          .filterNot(blockedMoves.contains)
          .orElse(search.bestMoveWithTime(context, budget, blockedMoves))
