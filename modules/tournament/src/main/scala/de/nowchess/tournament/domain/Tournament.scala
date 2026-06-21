package de.nowchess.tournament.domain

import jakarta.persistence.*
import scala.compiletime.uninitialized
import java.time.Instant

@Entity
@Table(name = "tournaments")
class Tournament:
  // scalafix:off DisableSyntax.var
  @Id
  var id: String = uninitialized

  @Column(nullable = false)
  var fullName: String = uninitialized

  var nbRounds: Int       = 0
  var clockLimit: Int     = 0
  var clockIncrement: Int = 0
  var rated: Boolean      = true

  @Column(nullable = false)
  var status: String = "created"

  var currentRound: Int = 0

  @Column(nullable = false)
  var createdBy: String = uninitialized

  var startsAt: Instant  = uninitialized
  var winnerId: String   = uninitialized
  var winnerName: String = uninitialized

  @Column(nullable = true)
  var originServerUrl: String = null
  // scalafix:on
