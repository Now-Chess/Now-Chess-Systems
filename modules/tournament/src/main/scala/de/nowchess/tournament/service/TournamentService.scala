package de.nowchess.tournament.service

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.tournament.client.{CoreCreateGameRequest, CoreGameClient, CorePlayerInfo, CoreTimeControl}
import de.nowchess.tournament.config.RedisConfig
import de.nowchess.tournament.domain.{Tournament, TournamentPairing, TournamentParticipant}
import de.nowchess.tournament.dto.{
  BotRef,
  Clock,
  CreateTournamentForm,
  PairingDto,
  ResultDto,
  Standing,
  TournamentDto,
  Variant,
}
import de.nowchess.tournament.error.TournamentError
import de.nowchess.tournament.repository.{PairingRepository, ParticipantRepository, TournamentRepository}
import io.quarkus.redis.datasource.RedisDataSource
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.util.{Failure, Success, Try}
import java.time.Instant

@ApplicationScoped
class TournamentService:

  private val log = Logger.getLogger(classOf[TournamentService])

  // scalafix:off DisableSyntax.var
  @Inject var tournamentRepository: TournamentRepository   = uninitialized
  @Inject var participantRepository: ParticipantRepository = uninitialized
  @Inject var pairingRepository: PairingRepository         = uninitialized
  @Inject var streamManager: TournamentStreamManager       = uninitialized

  @Inject
  @RestClient
  var coreGameClient: CoreGameClient = uninitialized

  @Inject var redis: RedisDataSource     = uninitialized
  @Inject var redisConfig: RedisConfig   = uninitialized
  @Inject var objectMapper: ObjectMapper = uninitialized
  // scalafix:on

  @Transactional
  def create(createdBy: String, form: CreateTournamentForm): Tournament =
    val t = new Tournament()
    t.id = scala.util.Random.alphanumeric.take(6).mkString
    t.fullName = form.name
    t.nbRounds = form.nbRounds
    t.clockLimit = form.clockLimit
    t.clockIncrement = form.clockIncrement
    t.rated = form.rated
    t.status = "created"
    t.currentRound = 0
    t.createdBy = createdBy
    tournamentRepository.persist(t)
    t

  def get(id: String): Option[Tournament] =
    tournamentRepository.findOptById(id)

  def list(): (List[Tournament], List[Tournament], List[Tournament]) =
    (
      tournamentRepository.findByStatus("created"),
      tournamentRepository.findByStatus("started"),
      tournamentRepository.findByStatus("finished"),
    )

  @Transactional
  def terminate(id: String, userId: String): Either[TournamentError, Unit] =
    for
      t <- tournamentRepository.findOptById(id).toRight(TournamentError.NotFound(id))
      _ <- Either.cond(t.createdBy == userId, (), TournamentError.NotDirector)
      _ <- Either.cond(t.status == "created", (), TournamentError.WrongStatus("created"))
    yield tournamentRepository.delete(t)

  @Transactional
  def join(id: String, botId: String, botName: String): Either[TournamentError, Unit] =
    for
      t <- tournamentRepository.findOptById(id).toRight(TournamentError.NotFound(id))
      _ <- Either.cond(t.status == "created", (), TournamentError.WrongStatus("created"))
      _ <- Either.cond(
        participantRepository.findByTournamentIdAndBotId(id, botId).isEmpty,
        (),
        TournamentError.AlreadyJoined,
      )
    yield
      val p = new TournamentParticipant()
      p.tournamentId = id
      p.botId = botId
      p.botName = botName
      participantRepository.persist(p)

  @Transactional
  def withdraw(id: String, botId: String): Either[TournamentError, Unit] =
    for
      t <- tournamentRepository.findOptById(id).toRight(TournamentError.NotFound(id))
      _ <- Either.cond(t.status == "created", (), TournamentError.WrongStatus("created"))
      p <- participantRepository.findByTournamentIdAndBotId(id, botId).toRight(TournamentError.NotJoined)
    yield participantRepository.delete(p)

  @Transactional
  def start(id: String, userId: String): Either[TournamentError, Tournament] =
    for
      t            <- tournamentRepository.findOptById(id).toRight(TournamentError.NotFound(id))
      _            <- Either.cond(t.createdBy == userId, (), TournamentError.NotDirector)
      _            <- Either.cond(t.status == "created", (), TournamentError.WrongStatus("created"))
      participants <- validateMinParticipants(id)
    yield
      t.status = "started"
      t.currentRound = 1
      t.startsAt = Instant.now()
      tournamentRepository.persist(t)
      streamManager.publish(t.id, """{"type":"tournamentStarted"}""")
      startRound(t, 1, participants)
      t

  private def validateMinParticipants(id: String): Either[TournamentError, List[TournamentParticipant]] =
    val ps = participantRepository.findByTournamentId(id)
    Either.cond(ps.size >= 2, ps, TournamentError.NotEnoughParticipants)

  private def startRound(t: Tournament, round: Int, participants: List[TournamentParticipant]): Unit =
    val pastPairings    = pairingRepository.findByTournamentId(t.id)
    val (pairs, byeOpt) = SwissPairingService.computePairings(participants, pastPairings)
    byeOpt.foreach(bye => createByePairing(t.id, round, bye))
    pairs.foreach { case (white, black) => createRealPairing(t.id, round, white, black, t) }
    streamManager.publish(t.id, s"""{"type":"roundStarted","round":$round}""")

  private def createByePairing(tournamentId: String, round: Int, bye: TournamentParticipant): Unit =
    val pairing = new TournamentPairing()
    pairing.tournamentId = tournamentId
    pairing.round = round
    pairing.blackId = bye.botId
    pairing.blackName = bye.botName
    pairing.winner = "bye"
    pairingRepository.persist(pairing)
    bye.points += 0.5
    bye.byeCount += 1
    participantRepository.persist(bye)

  private def createRealPairing(
      tournamentId: String,
      round: Int,
      white: TournamentParticipant,
      black: TournamentParticipant,
      t: Tournament,
  ): Unit =
    val tc = CoreTimeControl(Some(t.clockLimit), Some(t.clockIncrement), None)
    val req = CoreCreateGameRequest(
      Some(CorePlayerInfo(white.botId, white.botName)),
      Some(CorePlayerInfo(black.botId, black.botName)),
      Some(tc),
      Some("Authenticated"),
    )
    Try(coreGameClient.createGame(req)) match
      case Failure(ex) => log.errorf(ex, "Failed to create game for round %d in tournament %s", round, tournamentId)
      case Success(resp) =>
        val pairing = new TournamentPairing()
        pairing.tournamentId = tournamentId
        pairing.round = round
        pairing.whiteId = white.botId
        pairing.whiteName = white.botName
        pairing.blackId = black.botId
        pairing.blackName = black.botName
        pairing.gameId = resp.gameId
        pairingRepository.persist(pairing)
        streamManager.publishToBot(
          tournamentId,
          white.botId,
          s"""{"type":"gameStart","round":$round,"gameId":"${resp.gameId}","color":"white"}""",
        )
        streamManager.publishToBot(
          tournamentId,
          black.botId,
          s"""{"type":"gameStart","round":$round,"gameId":"${resp.gameId}","color":"black"}""",
        )
        publishBotGameStart(white.botName, resp.gameId, "white", white.botId)
        publishBotGameStart(black.botName, resp.gameId, "black", black.botId)

  private def publishBotGameStart(
      botName: String,
      gameId: String,
      playingAs: String,
      botAccountId: String,
  ): Unit =
    val channel = s"${redisConfig.prefix}:bot:$botName:events"
    val payload = objectMapper.writeValueAsString(
      Map(
        "type"         -> "gameStart",
        "gameId"       -> gameId,
        "playingAs"    -> playingAs,
        "difficulty"   -> 1500,
        "botAccountId" -> botAccountId,
      ),
    )
    Try(redis.pubsub(classOf[String]).publish(channel, payload)) match
      case Failure(ex) => log.warnf(ex, "Failed to publish gameStart to bot channel %s", channel)
      case Success(_)  => ()

  @Transactional
  def handleGameResult(gameId: String, result: String, pgn: String): Unit =
    pairingRepository.findByGameId(gameId).foreach { pairing =>
      val (winnerStr, wPts, bPts) = parseResult(result)
      pairing.winner = winnerStr
      pairing.moveList = pgn
      pairingRepository.persist(pairing)
      updateParticipantStats(pairing, wPts, bPts)
      checkRoundCompletion(pairing.tournamentId)
    }

  private def parseResult(result: String): (String, Double, Double) = result match
    case "1-0"     => ("white", 1.0, 0.0)
    case "0-1"     => ("black", 0.0, 1.0)
    case "1/2-1/2" => ("draw", 0.5, 0.5)
    case _         => ("draw", 0.5, 0.5)

  private def updateParticipantStats(pairing: TournamentPairing, wPts: Double, bPts: Double): Unit =
    Option(pairing.whiteId).foreach { wId =>
      participantRepository.findByTournamentIdAndBotId(pairing.tournamentId, wId).foreach { p =>
        p.points += wPts
        p.nbGames += 1
        if wPts == 1.0 then p.wins += 1 else if wPts == 0.5 then p.draws += 1 else p.losses += 1
        participantRepository.persist(p)
      }
    }
    participantRepository.findByTournamentIdAndBotId(pairing.tournamentId, pairing.blackId).foreach { p =>
      p.points += bPts
      p.nbGames += 1
      if bPts == 1.0 then p.wins += 1 else if bPts == 0.5 then p.draws += 1 else p.losses += 1
      participantRepository.persist(p)
    }

  private def checkRoundCompletion(tournamentId: String): Unit =
    tournamentRepository.findOptById(tournamentId).foreach { t =>
      val roundPairings = pairingRepository.findByTournamentIdAndRound(tournamentId, t.currentRound)
      val allDone       = roundPairings.nonEmpty && roundPairings.forall(p => Option(p.winner).isDefined)
      if allDone then onRoundComplete(t)
    }

  private def onRoundComplete(t: Tournament): Unit =
    streamManager.publish(t.id, s"""{"type":"roundFinished","round":${t.currentRound}}""")
    recomputeBuchholz(t.id)
    if t.currentRound >= t.nbRounds then finishTournament(t)
    else
      t.currentRound += 1
      tournamentRepository.persist(t)
      val participants = participantRepository.findByTournamentId(t.id)
      startRound(t, t.currentRound, participants)

  private def finishTournament(t: Tournament): Unit =
    val participants = sortedStandings(participantRepository.findByTournamentId(t.id))
    participants.headOption.foreach { winner =>
      t.winnerId = winner.botId
      t.winnerName = winner.botName
    }
    t.status = "finished"
    tournamentRepository.persist(t)
    val winnerInfo = participants.headOption
      .map(w => s"""{"id":"${w.botId}","name":"${w.botName}"}""")
      .getOrElse("null")
    streamManager.publish(t.id, s"""{"type":"tournamentFinished","winner":$winnerInfo}""")

  private def recomputeBuchholz(tournamentId: String): Unit =
    val participants = participantRepository.findByTournamentId(tournamentId)
    val pairings     = pairingRepository.findByTournamentId(tournamentId)
    val pointsById   = participants.map(p => p.botId -> p.points).toMap
    participants.foreach { p =>
      val opponentIds = pairings.flatMap(pair =>
        if pair.whiteId == p.botId then Some(pair.blackId)
        else if pair.blackId == p.botId && Option(pair.whiteId).isDefined then Some(pair.whiteId)
        else None,
      )
      p.tieBreak = opponentIds.flatMap(id => pointsById.get(id)).sum
      participantRepository.persist(p)
    }

  def getStandings(tournamentId: String): List[ResultDto] =
    val participants = sortedStandings(participantRepository.findByTournamentId(tournamentId))
    participants.zipWithIndex.map { case (p, idx) =>
      ResultDto(idx + 1, p.points, p.tieBreak, BotRef(p.botId, p.botName), p.nbGames, p.wins, p.draws, p.losses)
    }

  def getPairings(tournamentId: String, round: Int): List[PairingDto] =
    pairingRepository.findByTournamentIdAndRound(tournamentId, round).map(toPairingDto)

  def getAllPairings(tournamentId: String): List[TournamentPairing] =
    pairingRepository.findByTournamentId(tournamentId)

  def getResults(tournamentId: String): List[ResultDto] = getStandings(tournamentId)

  def toDto(t: Tournament, standings: List[ResultDto] = Nil): TournamentDto =
    val participants = participantRepository.findByTournamentId(t.id)
    TournamentDto(
      id = t.id,
      fullName = t.fullName,
      clock = Clock(t.clockLimit, t.clockIncrement),
      variant = Variant("standard", "Standard"),
      rated = t.rated,
      nbPlayers = participants.size,
      nbRounds = t.nbRounds,
      createdBy = t.createdBy,
      startsAt = Option(t.startsAt).map(_.toString),
      status = t.status,
      round = t.currentRound,
      standing = Standing(1, standings),
      winner = Option(t.winnerId).map(id => BotRef(id, t.winnerName)),
    )

  private def toPairingDto(p: TournamentPairing): PairingDto =
    PairingDto(
      id = p.id.toString,
      round = p.round,
      white = Option(p.whiteId).map(id => BotRef(id, p.whiteName)),
      black = BotRef(p.blackId, p.blackName),
      gameId = Option(p.gameId),
      winner = Option(p.winner),
    )

  private def sortedStandings(participants: List[TournamentParticipant]): List[TournamentParticipant] =
    participants.sortWith { (a, b) =>
      if a.points != b.points then a.points > b.points
      else if a.tieBreak != b.tieBreak then a.tieBreak > b.tieBreak
      else a.botName < b.botName
    }
