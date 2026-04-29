package de.nowchess.account.repository

import de.nowchess.account.domain.{BotAccount, OfficialBotAccount, UserAccount}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager

import java.util.UUID
import scala.jdk.CollectionConverters.*

@ApplicationScoped
class UserAccountRepository:

  @Inject
  // scalafix:off DisableSyntax.var
  var em: EntityManager = scala.compiletime.uninitialized
  // scalafix:on

  def findByUsername(username: String): Option[UserAccount] =
    em.createQuery("FROM UserAccount WHERE username = :username", classOf[UserAccount])
      .setParameter("username", username)
      .getResultList
      .stream()
      .findFirst()
      .map(Option(_))
      .orElse(None)

  def findById(id: UUID): Option[UserAccount] =
    Option(em.find(classOf[UserAccount], id))

  def persist(account: UserAccount): UserAccount =
    em.persist(account)
    account

  def findByEmail(email: String): Option[UserAccount] =
    em.createQuery("FROM UserAccount WHERE email = :email", classOf[UserAccount])
      .setParameter("email", email)
      .getResultList
      .asScala
      .headOption

  def findAll(): List[UserAccount] =
    em.createQuery("FROM UserAccount", classOf[UserAccount]).getResultList.asScala.toList

@ApplicationScoped
class BotAccountRepository:

  @Inject
  // scalafix:off DisableSyntax.var
  var em: EntityManager = scala.compiletime.uninitialized
  // scalafix:on

  def findById(id: UUID): Option[BotAccount] =
    Option(em.find(classOf[BotAccount], id))

  def findByOwner(ownerId: UUID): List[BotAccount] =
    em.createQuery("FROM BotAccount WHERE owner.id = :ownerId", classOf[BotAccount])
      .setParameter("ownerId", ownerId)
      .getResultList
      .asScala
      .toList

  def persist(bot: BotAccount): BotAccount =
    em.persist(bot)
    bot

  def delete(botId: UUID): Unit =
    em.find(classOf[BotAccount], botId) match
      case bot: BotAccount => em.remove(bot)

  def findByToken(token: String): Option[BotAccount] =
    em.createQuery("FROM BotAccount WHERE token = :token", classOf[BotAccount])
      .setParameter("token", token)
      .getResultList
      .asScala
      .headOption

@ApplicationScoped
class OfficialBotAccountRepository:

  @Inject
  // scalafix:off DisableSyntax.var
  var em: EntityManager = scala.compiletime.uninitialized
  // scalafix:on

  def findById(id: UUID): Option[OfficialBotAccount] =
    Option(em.find(classOf[OfficialBotAccount], id))

  def findAll(): List[OfficialBotAccount] =
    em.createQuery("FROM OfficialBotAccount", classOf[OfficialBotAccount]).getResultList.asScala.toList

  def persist(bot: OfficialBotAccount): OfficialBotAccount =
    em.persist(bot)
    bot

  def delete(botId: UUID): Unit =
    em.find(classOf[OfficialBotAccount], botId) match
      case bot: OfficialBotAccount => em.remove(bot)
