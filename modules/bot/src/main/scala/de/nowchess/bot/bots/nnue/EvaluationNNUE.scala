package de.nowchess.bot.bots.nnue

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.bot.ai.Evaluation

object EvaluationNNUE extends Evaluation:

  private val nnue = NNUE(NbaiLoader.loadDefault())

  val CHECKMATE_SCORE: Int = 10_000_000
  val DRAW_SCORE: Int      = 0

  /** Full-board evaluate — used as fallback and by non-search callers. */
  def evaluate(context: GameContext): Int = nnue.evaluate(context)

  // ── Accumulator hooks (incremental L1) ───────────────────────────────────

  override def initAccumulator(context: GameContext): Unit =
    nnue.initAccumulator(context.board)

  override def copyAccumulator(parentPly: Int, childPly: Int): Unit =
    nnue.copyAccumulator(parentPly, childPly)

  override def pushAccumulator(childPly: Int, move: Move, parent: GameContext, child: GameContext): Unit =
    // Use incremental updates, but recompute from scratch every 10 plies to prevent accumulation errors
    if childPly % 10 == 0 then nnue.recomputeAccumulator(childPly, child.board)
    else nnue.pushAccumulator(childPly, move, parent.board)

  override def evaluateAccumulator(ply: Int, context: GameContext, hash: Long): Int =
    nnue.evaluateAtPlyWithValidation(ply, context.turn, hash, context.board)
