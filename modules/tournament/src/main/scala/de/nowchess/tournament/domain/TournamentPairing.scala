package de.nowchess.tournament.domain

import jakarta.persistence.*
import scala.compiletime.uninitialized
import java.util.UUID

@Entity
@Table(name = "tournament_pairings")
class TournamentPairing:
  // scalafix:off DisableSyntax.var
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  var id: UUID = uninitialized

  @Column(nullable = false)
  var tournamentId: String = uninitialized

  var round: Int        = 0
  var whiteId: String   = uninitialized
  var whiteName: String = uninitialized

  @Column(nullable = false)
  var blackId: String = uninitialized

  @Column(nullable = false)
  var blackName: String = uninitialized

  var gameId: String   = uninitialized
  var winner: String   = uninitialized
  var moveList: String = uninitialized
  // scalafix:on
