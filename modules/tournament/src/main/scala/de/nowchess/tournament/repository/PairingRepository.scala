package de.nowchess.tournament.repository

import de.nowchess.tournament.domain.TournamentPairing
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import java.util.UUID

@ApplicationScoped
class PairingRepository:

  @Inject
  // scalafix:off DisableSyntax.var
  var em: EntityManager = uninitialized
  // scalafix:on

  def findByTournamentId(tournamentId: String): List[TournamentPairing] =
    em.createQuery("FROM TournamentPairing WHERE tournamentId = :tid", classOf[TournamentPairing])
      .setParameter("tid", tournamentId)
      .getResultList
      .asScala
      .toList

  def findByTournamentIdAndRound(tournamentId: String, round: Int): List[TournamentPairing] =
    em.createQuery(
      "FROM TournamentPairing WHERE tournamentId = :tid AND round = :round",
      classOf[TournamentPairing],
    ).setParameter("tid", tournamentId)
      .setParameter("round", round)
      .getResultList
      .asScala
      .toList

  def findByGameId(gameId: String): Option[TournamentPairing] =
    em.createQuery("FROM TournamentPairing WHERE gameId = :gid", classOf[TournamentPairing])
      .setParameter("gid", gameId)
      .getResultList
      .asScala
      .headOption

  def persist(p: TournamentPairing): TournamentPairing =
    if Option(p.id).isEmpty then
      em.persist(p)
      p
    else em.merge(p)
