package de.nowchess.account.repository

import de.nowchess.account.domain.{Challenge, ChallengeStatus}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager

import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*

@ApplicationScoped
class ChallengeRepository:

  @Inject
  // scalafix:off DisableSyntax.var
  var em: EntityManager = scala.compiletime.uninitialized
  // scalafix:on

  def findActiveByChallengerId(challengerId: UUID): List[Challenge] =
    em.createQuery(
      "FROM Challenge WHERE challenger.id = :cid AND status = :status AND expiresAt > :now",
      classOf[Challenge],
    ).setParameter("cid", challengerId)
      .setParameter("status", ChallengeStatus.Created)
      .setParameter("now", Instant.now())
      .getResultList
      .asScala
      .toList

  def findActiveByDestUserId(destUserId: UUID): List[Challenge] =
    em.createQuery(
      "FROM Challenge WHERE destUser.id = :uid AND status = :status AND expiresAt > :now",
      classOf[Challenge],
    ).setParameter("uid", destUserId)
      .setParameter("status", ChallengeStatus.Created)
      .setParameter("now", Instant.now())
      .getResultList
      .asScala
      .toList

  def findDuplicateChallenge(challengerId: UUID, destUserId: UUID): Option[Challenge] =
    em.createQuery(
      "FROM Challenge WHERE challenger.id = :cid AND destUser.id = :uid AND status = :status AND expiresAt > :now",
      classOf[Challenge],
    ).setParameter("cid", challengerId)
      .setParameter("uid", destUserId)
      .setParameter("status", ChallengeStatus.Created)
      .setParameter("now", Instant.now())
      .getResultList
      .asScala
      .headOption

  def findById(id: UUID): Option[Challenge] =
    Option(em.find(classOf[Challenge], id))

  def persist(challenge: Challenge): Challenge =
    em.persist(challenge)
    challenge

  def merge(challenge: Challenge): Challenge =
    em.merge(challenge)
