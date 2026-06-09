package de.nowchess.tournament.domain

import jakarta.persistence.*
import scala.compiletime.uninitialized
import java.util.UUID

@Entity
@Table(name = "tournament_participants")
class TournamentParticipant:
  // scalafix:off DisableSyntax.var
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  var id: UUID = uninitialized

  @Column(nullable = false)
  var tournamentId: String = uninitialized

  @Column(nullable = false)
  var botId: String = uninitialized

  @Column(nullable = false)
  var botName: String = uninitialized

  var points: Double   = 0.0
  var tieBreak: Double = 0.0
  var nbGames: Int     = 0
  var wins: Int        = 0
  var draws: Int       = 0
  var losses: Int      = 0
  var byeCount: Int    = 0
  // scalafix:on
