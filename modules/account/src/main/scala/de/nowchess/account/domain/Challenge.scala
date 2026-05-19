package de.nowchess.account.domain

import io.quarkus.hibernate.orm.panache.PanacheEntityBase
import jakarta.persistence.*
import scala.compiletime.uninitialized

import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "challenges")
class Challenge extends PanacheEntityBase:
  // scalafix:off DisableSyntax.var
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  var id: UUID = uninitialized

  @ManyToOne
  var challenger: UserAccount = uninitialized

  @ManyToOne
  var destUser: UserAccount = uninitialized

  @Convert(converter = classOf[ChallengeColorConverter])
  @Column(columnDefinition = "varchar(255)")
  var color: ChallengeColor = uninitialized

  @Convert(converter = classOf[ChallengeStatusConverter])
  @Column(columnDefinition = "varchar(255)")
  var status: ChallengeStatus = uninitialized

  @Convert(converter = classOf[DeclineReasonConverter])
  @Column(nullable = true, columnDefinition = "varchar(255)")
  var declineReason: DeclineReason = uninitialized

  @Column(nullable = true)
  var limitSeconds: java.lang.Integer = uninitialized

  @Column(nullable = true)
  var incrementSeconds: java.lang.Integer = uninitialized

  @Column(nullable = true)
  var daysPerMove: java.lang.Integer = uninitialized

  var createdAt: Instant = uninitialized

  var expiresAt: Instant = uninitialized

  @Column(nullable = true)
  var gameId: String = uninitialized
  // scalafix:on

  def gameIdOpt: Option[String]               = Option(gameId)
  def declineReasonOpt: Option[DeclineReason] = Option(declineReason)
  def limitSecondsOpt: Option[Int]            = Option(limitSeconds).map(_.intValue())
  def incrementSecondsOpt: Option[Int]        = Option(incrementSeconds).map(_.intValue())
  def daysPerMoveOpt: Option[Int]             = Option(daysPerMove).map(_.intValue())
