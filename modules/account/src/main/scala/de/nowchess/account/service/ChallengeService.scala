package de.nowchess.account.service

import de.nowchess.account.client.{
  CoreCreateGameRequest,
  CoreGameClient,
  CoreGameResponse,
  CorePlayerInfo,
  CoreTimeControl,
}
import de.nowchess.account.domain.{Challenge, ChallengeColor, ChallengeStatus, DeclineReason}
import de.nowchess.account.dto.{
  ChallengeDto,
  ChallengeListDto,
  ChallengeRequest,
  DeclineRequest,
  PlayerInfo,
  TimeControlDto,
}
import de.nowchess.account.error.ChallengeError
import de.nowchess.account.repository.{ChallengeRepository, UserAccountRepository}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import scala.compiletime.uninitialized

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

@ApplicationScoped
class ChallengeService:

  private val log = Logger.getLogger(classOf[ChallengeService])

  // scalafix:off DisableSyntax.var
  @Inject
  var userAccountRepository: UserAccountRepository = uninitialized

  @Inject
  var challengeRepository: ChallengeRepository = uninitialized

  @Inject
  @RestClient
  var coreGameClient: CoreGameClient = uninitialized

  @Inject
  var eventPublisher: EventPublisher = uninitialized
  // scalafix:on

  @Transactional
  def create(challengerId: UUID, destUsername: String, req: ChallengeRequest): Either[ChallengeError, Challenge] =
    for
      destUser <- userAccountRepository.findByUsername(destUsername).toRight(ChallengeError.UserNotFound(destUsername))
      challenger <- userAccountRepository.findById(challengerId).toRight(ChallengeError.ChallengerNotFound)
      _          <- Either.cond(challenger.id != destUser.id, (), ChallengeError.CannotChallengeSelf)
      _ <- Either.cond(
        challengeRepository.findDuplicateChallenge(challengerId, destUser.id).isEmpty,
        (),
        ChallengeError.DuplicateChallenge,
      )
      color <- parseColor(req.color)
    yield
      val challenge = new Challenge()
      challenge.challenger = challenger
      challenge.destUser = destUser
      challenge.color = color
      challenge.status = ChallengeStatus.Created
      challenge.timeControlType = req.timeControl.`type`
      challenge.timeControlLimit = req.timeControl.limit.map(java.lang.Integer.valueOf).orNull
      challenge.timeControlIncrement = req.timeControl.increment.map(java.lang.Integer.valueOf).orNull
      challenge.createdAt = Instant.now()
      challenge.expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
      challengeRepository.persist(challenge)
      try eventPublisher.publishChallengeCreated(destUser.id.toString, challenge.id.toString, challenger.username)
      catch case ex: Exception => log.warnf(ex, "Failed to notify dest user for challenge %s", challenge.id)
      challenge

  @Transactional
  def accept(challengeId: UUID, userId: UUID): Either[ChallengeError, Challenge] =
    for
      challenge <- challengeRepository.findById(challengeId).toRight(ChallengeError.ChallengeNotFound)
      _         <- Either.cond(challenge.status == ChallengeStatus.Created, (), ChallengeError.ChallengeNotActive)
      _         <- Either.cond(challenge.destUser.id == userId, (), ChallengeError.NotAuthorized)
      gameId    <- createGame(challenge)
    yield
      challenge.status = ChallengeStatus.Accepted
      challenge.gameId = gameId
      challengeRepository.merge(challenge)
      notifyBotIfNeeded(challenge, gameId)
      try eventPublisher.publishChallengeAccepted(challenge.challenger.id.toString, challenge.id.toString, gameId)
      catch case ex: Exception => log.warnf(ex, "Failed to notify challenger for game %s", gameId)
      challenge

  @Transactional
  def decline(challengeId: UUID, userId: UUID, req: DeclineRequest): Either[ChallengeError, Challenge] =
    for
      challenge <- challengeRepository.findById(challengeId).toRight(ChallengeError.ChallengeNotFound)
      _         <- Either.cond(challenge.status == ChallengeStatus.Created, (), ChallengeError.ChallengeNotActive)
      _         <- Either.cond(challenge.destUser.id == userId, (), ChallengeError.NotAuthorized)
      reason    <- parseDeclineReason(req.reason)
    yield
      challenge.status = ChallengeStatus.Declined
      challenge.declineReason = reason.orNull
      challengeRepository.merge(challenge)
      challenge

  @Transactional
  def cancel(challengeId: UUID, userId: UUID): Either[ChallengeError, Challenge] =
    for
      challenge <- challengeRepository.findById(challengeId).toRight(ChallengeError.ChallengeNotFound)
      _         <- Either.cond(challenge.status == ChallengeStatus.Created, (), ChallengeError.ChallengeNotActive)
      _         <- Either.cond(challenge.challenger.id == userId, (), ChallengeError.NotAuthorized)
    yield
      challenge.status = ChallengeStatus.Canceled
      challengeRepository.merge(challenge)
      challenge

  def findById(challengeId: UUID, userId: UUID): Either[ChallengeError, Challenge] =
    for
      challenge <- challengeRepository.findById(challengeId).toRight(ChallengeError.ChallengeNotFound)
      _ <- Either.cond(
        challenge.challenger.id == userId || challenge.destUser.id == userId,
        (),
        ChallengeError.NotAuthorized,
      )
    yield challenge

  def listForUser(userId: UUID): ChallengeListDto =
    val incoming = challengeRepository.findActiveByDestUserId(userId).map(toDto)
    val outgoing = challengeRepository.findActiveByChallengerId(userId).map(toDto)
    ChallengeListDto(in = incoming, out = outgoing)

  private def notifyBotIfNeeded(challenge: Challenge, gameId: String): Unit =
    val (white, black) = assignColors(challenge)
    List(challenge.challenger, challenge.destUser).foreach { user =>
      user.getBotAccounts.headOption.foreach { bot =>
        val playingAs = if white.id == user.id.toString then "white" else "black"
        try eventPublisher.publishGameStart(bot.id.toString, gameId, playingAs, 1400, bot.id.toString)
        catch case ex: Exception => log.warnf(ex, "Failed to notify bot for game %s", gameId)
      }
    }

  private def createGame(challenge: Challenge): Either[ChallengeError, String] =
    try
      val (white, black) = assignColors(challenge)
      val tc             = buildTimeControl(challenge)
      val req            = CoreCreateGameRequest(Some(white), Some(black), tc, Some("Authenticated"))
      Right(coreGameClient.createGame(req).gameId)
    catch case _ => Left(ChallengeError.GameCreationFailed)

  private def assignColors(challenge: Challenge): (CorePlayerInfo, CorePlayerInfo) =
    val challenger = CorePlayerInfo(challenge.challenger.id.toString, challenge.challenger.username)
    val destUser   = CorePlayerInfo(challenge.destUser.id.toString, challenge.destUser.username)
    challenge.color match
      case ChallengeColor.White => (challenger, destUser)
      case ChallengeColor.Black => (destUser, challenger)
      case ChallengeColor.Random =>
        if ThreadLocalRandom.current().nextBoolean() then (challenger, destUser) else (destUser, challenger)

  private def buildTimeControl(challenge: Challenge): Option[CoreTimeControl] =
    challenge.timeControlType match
      case "unlimited"      => None
      case "correspondence" => Some(CoreTimeControl(None, None, challenge.timeControlLimitOpt))
      case _ => Some(CoreTimeControl(challenge.timeControlLimitOpt, challenge.timeControlIncrementOpt, None))

  private def parseColor(raw: String): Either[ChallengeError, ChallengeColor] =
    raw.toLowerCase match
      case "white"  => Right(ChallengeColor.White)
      case "black"  => Right(ChallengeColor.Black)
      case "random" => Right(ChallengeColor.Random)
      case _        => Left(ChallengeError.InvalidColor(raw))

  private def parseDeclineReason(raw: Option[String]): Either[ChallengeError, Option[DeclineReason]] =
    raw match
      case None => Right(None)
      case Some(r) =>
        DeclineReason.values.find(_.toString.equalsIgnoreCase(r)) match
          case Some(reason) => Right(Some(reason))
          case None         => Left(ChallengeError.InvalidDeclineReason(r))

  def toDto(c: Challenge): ChallengeDto =
    ChallengeDto(
      id = c.id.toString,
      challenger = PlayerInfo(c.challenger.id.toString, c.challenger.username, c.challenger.rating),
      destUser = PlayerInfo(c.destUser.id.toString, c.destUser.username, c.destUser.rating),
      variant = "standard",
      color = c.color.toString.toLowerCase,
      timeControl = TimeControlDto(c.timeControlType, c.timeControlLimitOpt, c.timeControlIncrementOpt),
      status = c.status.toString.toLowerCase,
      declineReason = c.declineReasonOpt.map(_.toString.toLowerCase),
      gameId = c.gameIdOpt,
      createdAt = c.createdAt.toString,
      expiresAt = c.expiresAt.toString,
    )
