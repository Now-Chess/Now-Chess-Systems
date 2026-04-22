package de.nowchess.bot.bots

import de.nowchess.api.bot.Bot
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.bot.bots.classic.EvaluationClassic
import de.nowchess.bot.logic.AlphaBetaSearch
import de.nowchess.bot.util.PolyglotBook
import de.nowchess.bot.{BotDifficulty, BotMoveRepetition}
import de.nowchess.api.rules.RuleSet
import de.nowchess.rules.sets.DefaultRules

final class ClassicalBot(
    difficulty: BotDifficulty,
    rules: RuleSet = DefaultRules,
    book: Option[PolyglotBook] = None,
) extends Bot:

  private val search: AlphaBetaSearch = AlphaBetaSearch(rules, weights = EvaluationClassic)
  private val TIME_BUDGET_MS          = 1000L

  override val name: String = s"ClassicalBot(${difficulty.toString})"

  override def nextMove(context: GameContext): Option[Move] =
    val blockedMoves = BotMoveRepetition.blockedMoves(context)
    book
      .flatMap(_.probe(context))
      .filterNot(blockedMoves.contains)
      .orElse(search.bestMoveWithTime(context, TIME_BUDGET_MS, blockedMoves))
