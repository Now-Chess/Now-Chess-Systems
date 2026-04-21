package de.nowchess.chess.registry

import jakarta.enterprise.context.ApplicationScoped
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class GameRegistryImpl extends GameRegistry:
  private val games = ConcurrentHashMap[String, GameEntry]()
  private val rng   = new SecureRandom()

  def store(entry: GameEntry): Unit =
    games.put(entry.gameId, entry)

  def get(gameId: String): Option[GameEntry] =
    Option(games.get(gameId))

  def update(entry: GameEntry): Unit =
    games.put(entry.gameId, entry)

  def generateId(): String =
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    Iterator.continually(rng.nextInt(chars.length)).map(chars).take(8).mkString // NOSONAR
