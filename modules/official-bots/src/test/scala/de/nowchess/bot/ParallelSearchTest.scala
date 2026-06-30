package de.nowchess.bot

import de.nowchess.api.game.GameContext
import de.nowchess.bot.bots.classic.EvaluationClassic
import de.nowchess.bot.logic.{ParallelSearch, TranspositionTable}
import de.nowchess.rules.sets.DefaultRules
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ParallelSearchTest extends AnyFunSuite with Matchers:

  private def search(threads: Int): ParallelSearch =
    ParallelSearch(DefaultRules, TranspositionTable(), () => EvaluationClassic, threads)

  test("single-threaded coordinator returns a legal move on the initial position"):
    val move = search(1).bestMoveWithTime(GameContext.initial, 200L)
    move should not be None
    DefaultRules.allLegalMoves(GameContext.initial) should contain(move.get)

  test("multi-threaded Lazy SMP returns a legal move and does not crash under concurrency"):
    val parallel = search(4)
    for _ <- 1 to 5 do
      val move = parallel.bestMoveWithTime(GameContext.initial, 200L)
      move should not be None
      DefaultRules.allLegalMoves(GameContext.initial) should contain(move.get)

  test("numThreads below one is clamped to a single worker"):
    search(0).bestMoveWithTime(GameContext.initial, 100L) should not be None
