package de.nowchess.api.game

import de.nowchess.api.board.Color
import java.time.Instant
import java.time.temporal.ChronoUnit

sealed trait ClockState:
  def activeColor: Color
  def afterMove(movedColor: Color, at: Instant): Either[Color, ClockState]
  def remainingMs(color: Color, now: Instant): Long

final case class LiveClockState(
    whiteRemainingMs: Long,
    blackRemainingMs: Long,
    incrementMs: Long,
    lastTickAt: Instant,
    activeColor: Color,
) extends ClockState:
  def remainingMs(color: Color, now: Instant): Long =
    val stored = if color == Color.White then whiteRemainingMs else blackRemainingMs
    if color == activeColor then math.max(0L, stored - (now.toEpochMilli - lastTickAt.toEpochMilli))
    else stored

  def afterMove(movedColor: Color, at: Instant): Either[Color, ClockState] =
    val elapsed = at.toEpochMilli - lastTickAt.toEpochMilli
    val newRemaining =
      (if movedColor == Color.White then whiteRemainingMs else blackRemainingMs) - elapsed + incrementMs
    if newRemaining <= 0 then Left(movedColor)
    else
      val (w, b) =
        if movedColor == Color.White then (newRemaining, blackRemainingMs)
        else (whiteRemainingMs, newRemaining)
      Right(copy(whiteRemainingMs = w, blackRemainingMs = b, lastTickAt = at, activeColor = movedColor.opposite))

final case class CorrespondenceClockState(
    moveDeadline: Instant,
    daysPerMove: Int,
    activeColor: Color,
) extends ClockState:
  def remainingMs(color: Color, now: Instant): Long =
    math.max(0L, moveDeadline.toEpochMilli - now.toEpochMilli)

  def afterMove(movedColor: Color, at: Instant): Either[Color, ClockState] =
    if at.isAfter(moveDeadline) then Left(movedColor)
    else Right(copy(moveDeadline = at.plus(daysPerMove.toLong, ChronoUnit.DAYS), activeColor = movedColor.opposite))

object ClockState:
  def fromTimeControl(tc: TimeControl, activeColor: Color, now: Instant): Option[ClockState] =
    tc match
      case TimeControl.Clock(limit, inc) =>
        val ms = limit * 1000L
        Some(LiveClockState(ms, ms, inc * 1000L, now, activeColor))
      case TimeControl.Correspondence(days) =>
        Some(CorrespondenceClockState(now.plus(days.toLong, ChronoUnit.DAYS), days, activeColor))
      case TimeControl.Unlimited => None
