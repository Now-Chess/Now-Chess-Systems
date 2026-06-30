package de.nowchess.bot.bots

import de.nowchess.bot.Bot
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.api.rules.RuleSet
import de.nowchess.bot.ai.Evaluation
import de.nowchess.bot.bots.classic.EvaluationClassic
import de.nowchess.bot.bots.nnue.EvaluationNNUE
import de.nowchess.bot.logic.{ParallelSearch, TranspositionTable}
import de.nowchess.bot.util.PolyglotBook
import de.nowchess.bot.{Bot, BotDifficulty, BotMoveRepetition, Config, TimeControl}
import de.nowchess.rules.sets.DefaultRules

object HybridBot:

  private def defaultThreads: Int =
    sys.env.get("NNUE_SEARCH_THREADS").flatMap(_.toIntOption).filter(_ >= 1).getOrElse(1)

  // The veto re-search must share the move's budget, not double it: give the main search the bulk and
  // reserve a slice for the at-most-one veto re-search so a vetoed move never costs two full budgets.
  private val MainSearchShare = 0.7

  def apply(
      difficulty: BotDifficulty,
      rules: RuleSet = DefaultRules,
      book: Option[PolyglotBook] = None,
      nnueEvaluation: Evaluation = EvaluationNNUE,
      classicalEvaluation: Evaluation = EvaluationClassic,
      vetoReporter: String => Unit = println(_),
      searchThreads: Int = defaultThreads,
  ): Bot =
    // Use ParallelSearch to enable multi-threaded (SMP) search similar to NNUEBot
    val search = ParallelSearch(rules, TranspositionTable(), () => classicalEvaluation, searchThreads)
    new Bot:
      def move(context: GameContext, time: TimeControl): Option[Move] =
        val totalBudget  = if time.isClocked then time.budgetMs else Config.TIME_LIMIT_MS
        val mainBudget   = math.max(1L, (totalBudget * MainSearchShare).toLong)
        val vetoBudget   = math.max(1L, totalBudget - mainBudget)
        val blockedMoves = BotMoveRepetition.blockedMoves(context)

        def nnueScore(m: Move): Int      = nnueEvaluation.evaluate(rules.applyMove(context)(m))
        def classicalScore(m: Move): Int = classicalEvaluation.evaluate(rules.applyMove(context)(m))

        def refine(m: Move): Move =
          val moveNnue = nnueScore(m)
          if (classicalScore(m) - moveNnue).abs <= Config.VETO_THRESHOLD then m
          else
            search
              .bestMoveWithTime(context, vetoBudget, blockedMoves + m)
              .filterNot(blockedMoves.contains)
              .filter(alt => nnueScore(alt) < moveNnue)
              .map { alt =>
                vetoReporter(f"[Veto] ${m.from}->${m.to} replaced by ${alt.from}->${alt.to} — NNUE prefers it")
                alt
              }
              .getOrElse(m)

        book.flatMap(_.probe(context)).filterNot(blockedMoves.contains).orElse {
          search.bestMoveWithTime(context, mainBudget, blockedMoves).map(refine)
        }
