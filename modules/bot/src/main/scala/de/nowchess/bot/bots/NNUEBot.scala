package de.nowchess.bot.bots

import de.nowchess.api.bot.Bot
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.bot.bots.nnue.EvaluationNNUE
import de.nowchess.bot.logic.AlphaBetaSearch
import de.nowchess.bot.util.{PolyglotBook, ZobristHash}
import de.nowchess.bot.{BotDifficulty, BotMoveRepetition}
import de.nowchess.rules.RuleSet
import de.nowchess.rules.sets.DefaultRules

final class NNUEBot(
    difficulty: BotDifficulty,
    rules: RuleSet = DefaultRules,
    book: Option[PolyglotBook] = None,
) extends Bot:

  private val search: AlphaBetaSearch = AlphaBetaSearch(rules, weights = EvaluationNNUE)

  override val name: String = s"NNUEBot(${difficulty.toString})"

  override def nextMove(context: GameContext): Option[Move] =
    val blockedMoves = BotMoveRepetition.blockedMoves(context)
    book
      .flatMap(_.probe(context))
      .filterNot(blockedMoves.contains)
      .orElse {
        val moves = BotMoveRepetition.filterAllowed(context, rules.allLegalMoves(context))
        if moves.isEmpty then None
        else
          val scored   = batchEvaluateRoot(context, moves)
          val bestMove = scored.maxBy(_._2)._1
          search.bestMoveWithTime(context, allocateTime(scored), blockedMoves).orElse(Some(bestMove))
      }

  /** Evaluate all root moves shallowly via incremental NNUE accumulator updates. Returns (move, score) pairs with score
    * from the root player's perspective.
    */
  private def batchEvaluateRoot(context: GameContext, moves: List[Move]): List[(Move, Int)] =
    EvaluationNNUE.initAccumulator(context)
    val rootHash = ZobristHash.hash(context)
    moves.map { move =>
      val child     = rules.applyMove(context)(move)
      val childHash = ZobristHash.nextHash(context, rootHash, move, child)
      EvaluationNNUE.pushAccumulator(1, move, context, child)
      val score = -EvaluationNNUE.evaluateAccumulator(1, child, childHash)
      (move, score)
    }

  /** Allocate more time for complex positions; less when one move clearly dominates. */
  private def allocateTime(scored: List[(Move, Int)]): Long =
    val moveCount = scored.length
    if moveCount > 30 then 1500L
    else if moveCount < 5 then 500L
    else
      val scores = scored.map(_._2)
      val best   = scores.max
      val second = scores.filter(_ < best).maxOption.getOrElse(best)
      if best - second > 200 then 600L else 1000L
