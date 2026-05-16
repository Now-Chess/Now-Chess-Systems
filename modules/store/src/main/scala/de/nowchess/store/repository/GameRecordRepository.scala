package de.nowchess.store.repository

import de.nowchess.store.domain.GameRecord
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@ApplicationScoped
class GameRecordRepository:
  @Inject
  // scalafix:off DisableSyntax.var
  var em: EntityManager = uninitialized
  // scalafix:on

  def findByGameId(gameId: String): Option[GameRecord] =
    Option(em.find(classOf[GameRecord], gameId))

  def persist(record: GameRecord): Unit =
    em.persist(record)

  def merge(record: GameRecord): Unit =
    em.merge(record)

  def findByPlayerId(playerId: String, offset: Int, limit: Int): List[GameRecord] =
    em.createQuery(
      "SELECT g FROM GameRecord g WHERE g.whiteId = :id OR g.blackId = :id AND g.result != null ORDER BY g.updatedAt DESC",
      classOf[GameRecord],
    ).setParameter("id", playerId)
      .setFirstResult(offset)
      .setMaxResults(limit)
      .getResultList
      .asScala
      .toList

  def findExpiredLiveClockGames(nowMs: Long): List[GameRecord] =
    em.createQuery(
      "SELECT g FROM GameRecord g WHERE g.result IS NULL AND g.clockLastTickAt IS NOT NULL AND g.whiteRemainingMs IS NOT NULL",
      classOf[GameRecord],
    ).getResultList
      .asScala
      .toList
      .filter { g =>
        val remaining =
          if g.clockActiveColor == "white" then g.whiteRemainingMs.longValue else g.blackRemainingMs.longValue
        g.clockLastTickAt.longValue + remaining < nowMs
      }

  def findByPlayerIdRunning(playerId: String, offset: Int, limit: Int): List[GameRecord] =
    em.createQuery(
      "SELECT g FROM GameRecord g WHERE g.whiteId = :id OR g.blackId = :id AND g.result = null ORDER BY g.updatedAt DESC",
      classOf[GameRecord],
    ).setParameter("id", playerId)
      .setFirstResult(offset)
      .setMaxResults(limit)
      .getResultList
      .asScala
      .toList
