package de.nowchess.bot.ai

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move

trait Evaluation:

  def CHECKMATE_SCORE: Int
  def DRAW_SCORE: Int

  def evaluate(context: GameContext): Int

  // ── Accumulator hooks ─────────────────────────────────────────────────────
  // Default implementations fall back to full re-evaluation each call.
  // Override in NNUE-capable evaluators for incremental L1 speedup.

  /** Initialise the accumulator for the root position at ply 0. */
  def initAccumulator(context: GameContext): Unit = ()

  /** Copy parent ply's accumulator to childPly without move deltas (null-move). */
  def copyAccumulator(parentPly: Int, childPly: Int): Unit = ()

  /** Derive childPly's accumulator from parentPly by applying move deltas. */
  def pushAccumulator(childPly: Int, move: Move, parent: GameContext, child: GameContext): Unit = ()

  /** Evaluate from the pre-computed accumulator at ply, using hash for the eval cache. Falls back to full evaluate when
    * not overridden.
    */
  def evaluateAccumulator(ply: Int, context: GameContext, hash: Long): Int = evaluate(context)
