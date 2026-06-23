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

      def nnueScore(move: Move): Int      = nnueEvaluation.evaluate(rules.applyMove(context)(move))
      def classicalScore(move: Move): Int = classicalEvaluation.evaluate(rules.applyMove(context)(move))

      def refine(move: Move): Move =
        val moveNnue = nnueScore(move)
        if (classicalScore(move) - moveNnue).abs <= Config.VETO_THRESHOLD then move
        else
          search
            .bestMoveWithTime(context, Config.TIME_LIMIT_MS, blockedMoves + move)
            .filterNot(blockedMoves.contains)
            .filter(alt => nnueScore(alt) < moveNnue)
            .map { alt =>
              vetoReporter(f"[Veto] ${move.from}->${move.to} replaced by ${alt.from}->${alt.to} — NNUE prefers it")
              alt
            }
            .getOrElse(move)

      book.flatMap(_.probe(context)).filterNot(blockedMoves.contains).orElse {
        search.bestMoveWithTime(context, Config.TIME_LIMIT_MS, blockedMoves).map(refine)
      }
