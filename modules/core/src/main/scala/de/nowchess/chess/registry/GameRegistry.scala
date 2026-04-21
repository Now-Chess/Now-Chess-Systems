package de.nowchess.chess.registry

trait GameRegistry:
  def store(entry: GameEntry): Unit
  def get(gameId: String): Option[GameEntry]
  def update(entry: GameEntry): Unit
  def generateId(): String
