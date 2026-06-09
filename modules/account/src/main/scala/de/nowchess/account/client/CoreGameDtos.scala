package de.nowchess.account.client

case class CorePlayerInfo(id: String, displayName: String)
case class CoreTimeControl(limitSeconds: Option[Int], incrementSeconds: Option[Int], daysPerMove: Option[Int])
case class CoreCreateGameRequest(
    white: Option[CorePlayerInfo],
    black: Option[CorePlayerInfo],
    timeControl: Option[CoreTimeControl],
    mode: Option[String],
)
