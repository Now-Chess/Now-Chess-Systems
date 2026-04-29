package de.nowchess.chess.redis

sealed trait C2sMessage

object C2sMessage:
  case object Connected                                         extends C2sMessage
  case class Move(uci: String, playerId: Option[String] = None) extends C2sMessage
  case object Ping                                              extends C2sMessage
