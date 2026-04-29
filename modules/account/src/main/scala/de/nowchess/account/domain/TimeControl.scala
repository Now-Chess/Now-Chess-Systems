package de.nowchess.account.domain

sealed trait TimeControl

object TimeControl:
  case class Clock(limit: Int, increment: Int) extends TimeControl
  case object Unlimited                        extends TimeControl
