package de.nowchess.account.service

import de.nowchess.account.domain.{BotAccount, OfficialBotAccount, UserAccount}
import de.nowchess.account.dto.{LoginRequest, RegisterRequest}
import de.nowchess.account.error.AccountError
import de.nowchess.account.repository.{BotAccountRepository, OfficialBotAccountRepository, UserAccountRepository}
import io.quarkus.elytron.security.common.BcryptUtil
import io.smallrye.jwt.build.Jwt
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import scala.compiletime.uninitialized

import java.time.Instant
import java.util.UUID

@ApplicationScoped
class AccountService:

  // scalafix:off DisableSyntax.var
  @Inject
  var userAccountRepository: UserAccountRepository = uninitialized

  @Inject
  var botAccountRepository: BotAccountRepository = uninitialized

  @Inject
  var officialBotAccountRepository: OfficialBotAccountRepository = uninitialized
  // scalafix:on

  @Transactional
  def register(req: RegisterRequest): Either[AccountError, UserAccount] =
    if userAccountRepository.findByUsername(req.username).isDefined then Left(AccountError.UsernameTaken(req.username))
    else if userAccountRepository.findByEmail(req.email).isDefined then
      Left(AccountError.EmailAlreadyRegistered(req.email))
    else
      val account = new UserAccount()
      account.username = req.username
      account.email = req.email
      account.passwordHash = BcryptUtil.bcryptHash(req.password)
      account.createdAt = Instant.now()
      userAccountRepository.persist(account)
      Right(account)

  def login(req: LoginRequest): Either[AccountError, String] =
    userAccountRepository.findByUsername(req.username) match
      case None => Left(AccountError.InvalidCredentials)
      case Some(account) =>
        if !BcryptUtil.matches(req.password, account.passwordHash) then Left(AccountError.InvalidCredentials)
        else if account.banned then Left(AccountError.UserBanned)
        else
          Right(
            Jwt
              .issuer("nowchess")
              .subject(account.id.toString)
              .claim("username", account.username)
              .sign(),
          )

  def findByUsername(username: String): Option[UserAccount] =
    userAccountRepository.findByUsername(username)

  def findById(id: UUID): Option[UserAccount] =
    userAccountRepository.findById(id)

  @Transactional
  def createBotAccount(ownerId: UUID, botName: String): Either[AccountError, BotAccount] =
    userAccountRepository.findById(ownerId) match
      case None => Left(AccountError.UserNotFound)
      case Some(owner) =>
        val botAccounts = botAccountRepository.findByOwner(ownerId)
        if botAccounts.length >= 5 then Left(AccountError.BotLimitExceeded)
        else
          val bot = new BotAccount()
          bot.name = botName
          bot.owner = owner
          bot.token = generateBotToken(bot.id)
          bot.createdAt = Instant.now()
          botAccountRepository.persist(bot)
          Right(bot)

  def getBotAccounts(ownerId: UUID): List[BotAccount] =
    botAccountRepository.findByOwner(ownerId)

  def getBotAccountWithOwnerCheck(botId: UUID, ownerId: UUID): Option[Option[BotAccount]] =
    botAccountRepository.findById(botId) match
      case None      => Some(None)
      case Some(bot) => Some(Option(bot).filter(_.owner.id == ownerId))

  @Transactional
  def deleteBotAccount(botId: UUID): Either[AccountError, Unit] =
    botAccountRepository.findById(botId) match
      case None => Left(AccountError.BotNotFound)
      case Some(_) =>
        botAccountRepository.delete(botId)
        Right(())

  @Transactional
  def updateBotName(botId: UUID, ownerId: UUID, newName: String): Either[AccountError, BotAccount] =
    botAccountRepository.findById(botId) match
      case None => Left(AccountError.BotNotFound)
      case Some(bot) =>
        if bot.owner.id != ownerId then Left(AccountError.NotAuthorized)
        else
          bot.name = newName
          botAccountRepository.persist(bot)
          Right(bot)

  @Transactional
  def rotateBotToken(botId: UUID, ownerId: UUID): Either[AccountError, BotAccount] =
    botAccountRepository.findById(botId) match
      case None => Left(AccountError.BotNotFound)
      case Some(bot) =>
        if bot.owner.id != ownerId then Left(AccountError.NotAuthorized)
        else
          bot.token = generateBotToken(botId)
          botAccountRepository.persist(bot)
          Right(bot)

  @Transactional
  def createOfficialBotAccount(botName: String): Either[AccountError, OfficialBotAccount] =
    val bot = new OfficialBotAccount()
    bot.name = botName
    bot.createdAt = Instant.now()
    officialBotAccountRepository.persist(bot)
    Right(bot)

  def getOfficialBotAccounts(): List[OfficialBotAccount] =
    officialBotAccountRepository.findAll()

  @Transactional
  def deleteOfficialBotAccount(botId: UUID): Either[AccountError, Unit] =
    officialBotAccountRepository.findById(botId) match
      case None => Left(AccountError.BotNotFound)
      case Some(_) =>
        officialBotAccountRepository.delete(botId)
        Right(())

  private def generateBotToken(botId: UUID): String =
    Jwt
      .issuer("nowchess")
      .subject(botId.toString)
      .expiresAt(Long.MaxValue)
      .claim("type", "bot")
      .sign()

  @Transactional
  def banUser(userId: UUID): Either[AccountError, UserAccount] =
    userAccountRepository.findById(userId) match
      case None => Left(AccountError.UserNotFound)
      case Some(user) =>
        user.banned = true
        user.botAccounts.forEach(_.banned = true)
        userAccountRepository.persist(user)
        Right(user)

  @Transactional
  def unbanUser(userId: UUID): Either[AccountError, UserAccount] =
    userAccountRepository.findById(userId) match
      case None => Left(AccountError.UserNotFound)
      case Some(user) =>
        user.banned = false
        user.botAccounts.forEach(_.banned = false)
        userAccountRepository.persist(user)
        Right(user)
