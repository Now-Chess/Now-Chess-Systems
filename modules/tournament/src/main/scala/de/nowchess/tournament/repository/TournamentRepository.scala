package de.nowchess.tournament.repository

import de.nowchess.tournament.domain.Tournament
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@ApplicationScoped
class TournamentRepository:

  @Inject
  // scalafix:off DisableSyntax.var
  var em: EntityManager = uninitialized
  // scalafix:on

  def findOptById(id: String): Option[Tournament] =
    Option(em.find(classOf[Tournament], id))

  def findByStatus(status: String): List[Tournament] =
    em.createQuery("FROM Tournament WHERE status = :status", classOf[Tournament])
      .setParameter("status", status)
      .getResultList
      .asScala
      .toList

  def persist(t: Tournament): Tournament =
    if em.contains(t) then t else em.merge(t)

  def delete(t: Tournament): Unit =
    val managed = if em.contains(t) then t else em.merge(t)
    em.remove(managed)
