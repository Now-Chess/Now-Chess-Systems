package de.nowchess.bot.logic

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.api.rules.RuleSet
import de.nowchess.bot.ai.Evaluation
import de.nowchess.rules.sets.DefaultRules

import java.util.concurrent.{Callable, Executors}
import scala.jdk.CollectionConverters.*

/** Lazy SMP search coordinator.
  *
  * Runs `numThreads` independent [[AlphaBetaSearch]] workers over one shared transposition table for the same time
  * budget. Every worker has its own evaluator (independent NNUE accumulator) and move-ordering state, but they share
  * the thread-safe TT, so faster-progressing threads deepen entries the others reuse. Only the main worker's move is
  * returned; helpers exist purely to enrich the shared TT.
  *
  * `numThreads <= 1` runs a single worker via the ordinary clearing entry point, byte-identical to sequential
  * [[AlphaBetaSearch]].
  */
final class ParallelSearch(
    rules: RuleSet = DefaultRules,
    tt: TranspositionTable = TranspositionTable(),
    evalFactory: () => Evaluation,
    numThreads: Int = 1,
):

  private val threadCount = math.max(1, numThreads)
  private val workers     = Vector.fill(threadCount)(AlphaBetaSearch(rules, tt, evalFactory()))

  def bestMoveWithTime(
      context: GameContext,
      timeBudgetMs: Long,
      excludedRootMoves: Set[Move] = Set.empty,
      hints: Map[Move, Int] = Map.empty,
  ): Option[Move] =
    if threadCount == 1 then workers.head.bestMoveWithTime(context, timeBudgetMs, excludedRootMoves, hints)
    else runParallel(context, timeBudgetMs, excludedRootMoves, hints)

  private def runParallel(
      context: GameContext,
      timeBudgetMs: Long,
      excludedRootMoves: Set[Move],
      hints: Map[Move, Int],
  ): Option[Move] =
    tt.clear()
    val pool = Executors.newFixedThreadPool(threadCount)
    try
      val tasks = workers.map { worker =>
        new Callable[Option[Move]]:
          def call(): Option[Move] =
            worker.bestMoveWithTimeSharedTt(context, timeBudgetMs, excludedRootMoves, hints)
      }
      pool.invokeAll(tasks.asJava).get(0).get()
    finally pool.shutdownNow()
