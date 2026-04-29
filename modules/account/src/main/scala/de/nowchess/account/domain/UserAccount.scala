package de.nowchess.account.domain

import io.quarkus.hibernate.orm.panache.PanacheEntityBase
import jakarta.persistence.*
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_accounts")
class UserAccount extends PanacheEntityBase:
  // scalafix:off DisableSyntax.var
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  var id: UUID = uninitialized

  @Column(unique = true, nullable = false)
  var username: String = uninitialized

  @Column(unique = true, nullable = false)
  var email: String = uninitialized

  var passwordHash: String = uninitialized

  var rating: Int = 1500

  var createdAt: Instant = uninitialized

  var banned: Boolean = false

  @OneToMany(mappedBy = "owner", cascade = Array(CascadeType.ALL), orphanRemoval = true)
  var botAccounts: java.util.List[BotAccount] = uninitialized
  // scalafix:on

  def getBotAccounts: List[BotAccount] = Option(botAccounts).map(_.asScala.toList).getOrElse(Nil)

@Entity
@Table(name = "bot_accounts")
class BotAccount extends PanacheEntityBase:
  // scalafix:off DisableSyntax.var
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  var id: UUID = uninitialized

  @Column(nullable = false)
  var name: String = uninitialized

  @ManyToOne(optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  var owner: UserAccount = uninitialized

  @Column(unique = true, nullable = false, length = 256)
  var token: String = uninitialized

  var rating: Int = 1500

  var createdAt: Instant = uninitialized

  var banned: Boolean = false
  // scalafix:on

@Entity
@Table(name = "official_bot_accounts")
class OfficialBotAccount extends PanacheEntityBase:
  // scalafix:off DisableSyntax.var
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  var id: UUID = uninitialized

  @Column(nullable = false)
  var name: String = uninitialized

  var rating: Int = 1500

  var createdAt: Instant = uninitialized
  // scalafix:on
