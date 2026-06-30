package de.nowchess.bot

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move

/** Remaining wall-clock for the side to move and the Fischer increment, both in milliseconds. [[TimeControl.Unlimited]]
  * is the sentinel for callers without a real clock (local play, self-play, tests): bots then fall back to their own
  * fixed budgets.
  */
final case class TimeControl(remainingMs: Long, incrementMs: Long):
  def isClocked: Boolean = remainingMs >= 0L
  def budgetMs: Long     = TimeControl.budget(remainingMs, incrementMs)

object TimeControl:
  val Unlimited: TimeControl = TimeControl(-1L, 0L)

  private val OverheadMs = 1500L
  private val PanicMs    = 20000L
  private val MaxBudget  = 8000L
  private val PanicCap   = 2500L
  private val FloorMs    = 50L

  def budget(remainingMs: Long, incrementMs: Long): Long =
    val usable = math.max(0L, remainingMs - OverheadMs)
    if usable <= 0L then FloorMs
    else if remainingMs < PanicMs then clamp(usable / 15 + incrementMs / 2, PanicCap)
    else clamp(usable / 30 + incrementMs * 4 / 5, MaxBudget)

  private def clamp(value: Long, ceiling: Long): Long =
    math.max(FloorMs, math.min(value, ceiling))

trait Bot:
  def move(context: GameContext, time: TimeControl): Option[Move]
  def apply(context: GameContext): Option[Move]                    = move(context, TimeControl.Unlimited)
  def apply(context: GameContext, time: TimeControl): Option[Move] = move(context, time)
