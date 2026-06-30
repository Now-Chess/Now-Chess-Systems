package de.nowchess.bot.bots.nnue

/** Immutable, shareable NNUE parameters.
  *
  * Heavy to build (transposes the L1 weight matrix once, ~98304 × accSize floats) but read-only thereafter, so a single
  * instance is safely shared across many per-thread [[NNUE]] evaluators. Holds no accumulator or scratch state — those
  * live on each [[NNUE]] instance — which is what makes parallel search (independent evaluators sharing these weights)
  * possible without duplicating the weight matrix.
  */
class NNUEWeights(val model: NbaiModel):

  val HALF_SIZE: Int   = 49152                     // 64 king-squares × 12 piece-types × 64 piece-squares
  val featureSize: Int = model.layers(0).inputSize // 98304 (= HALF_SIZE * 2) for king-relative
  val accSize: Int     = model.layers(0).outputSize

  // Column-major L1 weights: l1WeightsT(featureIdx * accSize + outputIdx)
  val l1WeightsT: Array[Float] =
    val w = model.weights(0).weights
    val t = new Array[Float](featureSize * accSize)
    for j <- 0 until featureSize; i <- 0 until accSize do t(j * accSize + i) = w(i * featureSize + j)
    t
