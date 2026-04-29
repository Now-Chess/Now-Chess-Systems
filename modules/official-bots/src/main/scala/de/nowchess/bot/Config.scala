package de.nowchess.bot

object Config:

  /** Threshold in centipawns: if classical evaluation differs from NNUE by more than this, the move is vetoed (not
    * accepted as a suggestion).
    */
  val VETO_THRESHOLD: Int = 150

  /** Time budget per move for iterative deepening (milliseconds). */
  val TIME_LIMIT_MS: Long = 2000L
