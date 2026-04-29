package de.nowchess.api.dto

/** Snapshot of remaining clock time for both players in milliseconds. -1 indicates the value is not applicable (e.g.
  * inactive player in correspondence chess).
  */
final case class ClockDto(
    whiteRemainingMs: Long,
    blackRemainingMs: Long,
)
