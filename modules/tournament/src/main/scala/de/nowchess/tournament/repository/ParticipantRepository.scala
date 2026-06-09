package de.nowchess.tournament.repository

import de.nowchess.tournament.domain.TournamentParticipant
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import java.util.UUID

@ApplicationScoped
class ParticipantRepository:

  @Inject
  // scalafix:off DisableSyntax.var
  var em: EntityManager = uninitialized
  // scalafix:on

  def findByTournamentId(tournamentId: String): List[TournamentParticipant] =
    em.createQuery("FROM TournamentParticipant WHERE tournamentId = :tid", classOf[TournamentParticipant])
      .setParameter("tid", tournamentId)
      .getResultList
      .asScala
      .toList

  def findByTournamentIdAndBotId(tournamentId: String, botId: String): Option[TournamentParticipant] =
    em.createQuery(
      "FROM TournamentParticipant WHERE tournamentId = :tid AND botId = :bid",
      classOf[TournamentParticipant],
    ).setParameter("tid", tournamentId)
      .setParameter("bid", botId)
      .getResultList
      .asScala
      .headOption

  def persist(p: TournamentParticipant): TournamentParticipant =
    if Option(p.id).isEmpty then
      em.persist(p)
      p
    else em.merge(p)

  def delete(p: TournamentParticipant): Unit =
    em.remove(if em.contains(p) then p else em.merge(p))
