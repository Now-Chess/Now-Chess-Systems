package de.nowchess.bot.bots

import de.nowchess.bot.Bot
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.api.rules.RuleSet
import de.nowchess.bot.ai.Evaluation
import de.nowchess.bot.bots.classic.EvaluationClassic
import de.nowchess.bot.bots.nnue.EvaluationNNUE
import de.nowchess.bot.logic.{AlphaBetaSearch, TranspositionTable}
import de.nowchess.bot.util.PolyglotBook
import de.nowchess.bot.{BotDifficulty, BotMoveRepetition, Config}
import de.nowchess.rules.sets.DefaultRules

object HybridBot:
  def apply(
      difficulty: BotDifficulty,
      rules: RuleSet = DefaultRules,
      book: Option[PolyglotBook] = None,
      nnueEvaluation: Evaluation = EvaluationNNUE,
      classicalEvaluation: Evaluation = EvaluationClassic,
      vetoReporter: String => Unit = println(_),
  ): Bot =
    val search = AlphaBetaSearch(rules, TranspositionTable(), classicalEvaluation)
    context =>
      val blockedMoves = BotMoveRepetition.blockedMoves(context)
      book.flatMap(_.probe(context)).filterNot(blockedMoves.contains).orElse {
        search.bestMoveWithTime(context, Config.TIME_LIMIT_MS, blockedMoves).map { move =>
          val next       = rules.applyMove(context)(move)
          val staticNnue = nnueEvaluation.evaluate(next)
          val classical  = classicalEvaluation.evaluate(next)
          val diff       = (classical - staticNnue).abs
          if diff > Config.VETO_THRESHOLD then
            vetoReporter(
              f"[Veto] ${move.from}->${move.to}: nnue=$staticNnue  classical=$classical  diff=$diff — flagged but trusted (deep search)",
            )
          move
        }
      }
