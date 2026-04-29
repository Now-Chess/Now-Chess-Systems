package de.nowchess.bot.bots

import de.nowchess.bot.Bot
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.api.rules.RuleSet
import de.nowchess.bot.bots.nnue.EvaluationNNUE
import de.nowchess.bot.logic.AlphaBetaSearch
import de.nowchess.bot.util.{PolyglotBook, ZobristHash}
import de.nowchess.bot.{BotDifficulty, BotMoveRepetition}
import de.nowchess.rules.sets.DefaultRules

object NNUEBot:
  def apply(
      difficulty: BotDifficulty,
      rules: RuleSet = DefaultRules,
      book: Option[PolyglotBook] = None,
  ): Bot =
    val search = AlphaBetaSearch(rules, weights = EvaluationNNUE)
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
            search.bestMoveWithTime(context, allocateTime(scored), blockedMoves).orElse(Some(bestMove))
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
