package de.nowchess.bot.bots.nnue

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.bot.ai.Evaluation

/** One independent NNUE evaluator: wraps its own [[NNUE]] (own accumulator stack, scratch buffers and eval cache) plus
  * the endgame mop-up correction. Independent instances may run concurrently as long as they share only the read-only
  * [[NNUEWeights]].
  */
final class NNUEEvaluator(nnue: NNUE) extends Evaluation:

  val CHECKMATE_SCORE: Int = 10_000_000
  val DRAW_SCORE: Int      = 0

  /** Full-board evaluate — used as fallback and by non-search callers. */
  def evaluate(context: GameContext): Int = nnue.evaluate(context) + MopUp.score(context)

  // ── Accumulator hooks (incremental L1) ───────────────────────────────────

  override def initAccumulator(context: GameContext): Unit =
    nnue.initAccumulator(context.board)

  override def copyAccumulator(parentPly: Int, childPly: Int): Unit =
    nnue.copyAccumulator(parentPly, childPly)

  override def pushAccumulator(childPly: Int, move: Move, parent: GameContext, child: GameContext): Unit =
    // Recompute every 10 plies to prevent floating-point drift; king moves always recompute internally
    if childPly % 10 == 0 then nnue.recomputeAccumulator(childPly, child.board)
    else nnue.pushAccumulator(childPly, move, parent.board, child.board)

  override def evaluateAccumulator(ply: Int, context: GameContext, hash: Long): Int =
    nnue.evaluateAtPlyWithValidation(ply, context.turn, hash, context.board) + MopUp.score(context)

/** Default singleton evaluator plus a factory for independent per-thread evaluators that share the loaded weights. */
object EvaluationNNUE extends Evaluation:

  private val weights = NNUEWeights(NbaiLoader.loadDefault())
  private val default = NNUEEvaluator(NNUE(weights))

  /** Build a fresh evaluator backed by its own [[NNUE]] but sharing the immutable [[weights]] — one per search thread.
    */
  def freshEvaluator(): Evaluation = NNUEEvaluator(NNUE(weights))

  val CHECKMATE_SCORE: Int = default.CHECKMATE_SCORE
  val DRAW_SCORE: Int      = default.DRAW_SCORE

  def evaluate(context: GameContext): Int = default.evaluate(context)

  override def initAccumulator(context: GameContext): Unit = default.initAccumulator(context)

  override def copyAccumulator(parentPly: Int, childPly: Int): Unit = default.copyAccumulator(parentPly, childPly)

  override def pushAccumulator(childPly: Int, move: Move, parent: GameContext, child: GameContext): Unit =
    default.pushAccumulator(childPly, move, parent, child)

  override def evaluateAccumulator(ply: Int, context: GameContext, hash: Long): Int =
    default.evaluateAccumulator(ply, context, hash)
