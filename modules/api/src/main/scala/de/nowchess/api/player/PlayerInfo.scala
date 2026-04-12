package de.nowchess.api.player

/** An opaque player identifier.
  *
  * Wraps a plain String so that IDs are not accidentally interchanged with other String values at compile time.
  */
opaque type PlayerId = String

object PlayerId:
  def apply(value: String): PlayerId         = value
  extension (id: PlayerId) def value: String = id

/** The minimal cross-service identity stub for a player.
  *
  * Full profile data (email, rating history, etc.) lives in the user-management service. Only what every service needs
  * is held here.
  *
  * @param id
  *   unique identifier
  * @param displayName
  *   human-readable name shown in the UI
  */
final case class PlayerInfo(
    id: PlayerId,
    displayName: String,
)
