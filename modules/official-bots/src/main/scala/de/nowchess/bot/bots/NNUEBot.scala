package de.nowchess.bot.bots

import de.nowchess.bot.Bot
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.api.rules.RuleSet
import de.nowchess.bot.bots.nnue.EvaluationNNUE
import de.nowchess.bot.logic.{ParallelSearch, TranspositionTable}
import de.nowchess.bot.util.{PolyglotBook, ZobristHash}
import de.nowchess.bot.{BotDifficulty, BotMoveRepetition}
import de.nowchess.rules.sets.DefaultRules

object NNUEBot:
  private def defaultThreads: Int =
    sys.env.get("NNUE_SEARCH_THREADS").flatMap(_.toIntOption).filter(_ >= 1).getOrElse(1)

  def apply(
      difficulty: BotDifficulty,
      rules: RuleSet = DefaultRules,
      book: Option[PolyglotBook] = None,
      fixedMoveTimeMs: Option[Long] = None,
      searchThreads: Int = defaultThreads,
  ): Bot =
    val search = ParallelSearch(rules, TranspositionTable(), () => EvaluationNNUE.freshEvaluator(), searchThreads)
    context =>
      val blockedMoves = BotMoveRepetition.blockedMoves(context)
      book
        .flatMap(_.probe(context))
        .filterNot(blockedMoves.contains)
        .orElse {
          val moves = BotMoveRepetition.filterAllowed(context, rules.allLegalMoves(context))
          if moves.isEmpty then None
          else
            val scored   = batchEvaluateRoot(rules, context, moves)
            val bestMove = scored.maxBy(_._2)._1
            val budget   = fixedMoveTimeMs.getOrElse(allocateTime(scored))
            search.bestMoveWithTime(context, budget, blockedMoves, scored.toMap).orElse(Some(bestMove))
        }

  private def batchEvaluateRoot(rules: RuleSet, context: GameContext, moves: List[Move]): List[(Move, Int)] =
    EvaluationNNUE.initAccumulator(context)
    val rootHash = ZobristHash.hash(context)
    moves.map { move =>
      val child     = rules.applyMove(context)(move)
      val childHash = ZobristHash.nextHash(context, rootHash, move, child)
      EvaluationNNUE.pushAccumulator(1, move, context, child)
      val score = -EvaluationNNUE.evaluateAccumulator(1, child, childHash)
      (move, score)
    }

  private def allocateTime(scored: List[(Move, Int)]): Long =
    val moveCount = scored.length
    if moveCount > 30 then 1500L
    else if moveCount < 5 then 500L
    else
      val scores = scored.map(_._2)
      val best   = scores.max
      val second = scores.filter(_ < best).maxOption.getOrElse(best)
      if best - second > 200 then 600L else 1000L
