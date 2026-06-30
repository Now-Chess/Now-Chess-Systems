package de.nowchess.bot.service

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.bot.{Bot, BotController, TimeControl}
import de.nowchess.bot.client.AccountServiceClient
import de.nowchess.bot.config.RedisConfig
import de.nowchess.io.fen.FenParser
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.runtime.Startup
import jakarta.annotation.{PostConstruct, PreDestroy}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.client.{Client, ClientBuilder, Entity}
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}
import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.util.concurrent.{ConcurrentHashMap, ExecutorService, Executors}
import java.util.concurrent.atomic.AtomicReference

@Startup
@ApplicationScoped
class TournamentBotGamePlayer:

  private val log = Logger.getLogger(classOf[TournamentBotGamePlayer])

  // scalafix:off DisableSyntax.var
  @Inject var objectMapper: ObjectMapper                             = uninitialized
  @Inject var botController: BotController                           = uninitialized
  @Inject var redis: RedisDataSource                                 = uninitialized
  @Inject var redisConfig: RedisConfig                               = uninitialized
  @Inject @RestClient var accountServiceClient: AccountServiceClient = uninitialized
  // scalafix:on DisableSyntax.var

  private val client: Client           = ClientBuilder.newClient()
  private val workers: ExecutorService = Executors.newCachedThreadPool()
  private val activeGames              = ConcurrentHashMap.newKeySet[String]()
  private val joinedTournaments        = ConcurrentHashMap.newKeySet[String]()

  private val hardestDifficulty  = "expert"
  private val autoJoinIntervalMs = 15000L
  // Detect the opponent's move fast: every poll spent waiting runs our clock without us thinking.
  private val pollIntervalMs = 250L

  private val gameTerminalStatuses =
    Set("checkmate", "stalemate", "draw", "resigned", "timeout", "aborted", "finished")

  // scalafix:off DisableSyntax.var
  @volatile private var running                       = true
  @volatile private var autoJoinToken: Option[String] = None
  // scalafix:on DisableSyntax.var

  val tournamentServiceUrl: String =
    System.getenv().asScala.getOrElse("TOURNAMENT_SERVICE_URL", "http://localhost:8086")

  val autoJoinServerUrl: String =
    System.getenv().asScala.getOrElse("TOURNAMENT_AUTO_JOIN_URL", "http://141.37.123.132:8086")

  @PostConstruct
  def initialize(): Unit =
    val env        = System.getenv().asScala.toMap
    val difficulty = env.getOrElse("TOURNAMENT_BOT_DIFFICULTY", "medium")
    val token      = resolveToken(difficulty)
    parkOnStartup(token)
    TournamentBotConfig.fromEnvWithToken(env, token) match
      case None =>
        log.info("Tournament bot disabled — set TOURNAMENT_ID to enable")
      case Some(cfg) =>
        log.infof("Tournament bot enabled — server=%s tournament=%s bot=%s", cfg.serverUrl, cfg.tournamentId, cfg.botId)
        startAsync(cfg)
    startAutoJoin()

  private def startAutoJoin(): Unit =
    val thread = new Thread(() => autoJoinLoop(), "TournamentBot-auto-join")
    thread.setDaemon(true)
    thread.start()
    log.infof("Auto-join enabled — server=%s difficulty=%s", autoJoinServerUrl, hardestDifficulty)

  private def autoJoinLoop(): Unit =
    while running do
      Try(autoJoinScan()).failed.foreach(ex => log.warnf(ex, "Auto-join scan failed"))
      sleep(autoJoinIntervalMs)

  private def autoJoinScan(): Unit =
    resolveAutoJoinToken().foreach { token =>
      TournamentBotConfig.jwtSubject(token).foreach { botId =>
        val open = openTournaments()
        log.infof("Auto-join scan — server=%s open tournaments=%d bot=%s", autoJoinServerUrl, open.size, botId)
        open.foreach { tournamentId =>
          if joinedTournaments.add(tournamentId) then
            val cfg = TournamentBotConfig(autoJoinServerUrl, tournamentId, token, botId, hardestDifficulty)
            if !joinedOrParticipating(cfg) then joinedTournaments.remove(tournamentId)
        }
        playPendingGames(token, botId)
      }
    }

  // The tournament-server does not reliably replay gameStart to late subscribers, so we cannot
  // depend on the event stream to discover games. Poll each joined tournament for our active game.
  private def playPendingGames(token: String, botId: String): Unit =
    joinedTournaments.forEach { tournamentId =>
      val cfg = TournamentBotConfig(autoJoinServerUrl, tournamentId, token, botId, hardestDifficulty)
      pendingGame(cfg).foreach { (gameId, color) =>
        if activeGames.add(gameId) then
          log.infof("Polled active game %s as %s in tournament %s", gameId, color, tournamentId)
          workers.submit(new Runnable { def run(): Unit = playGame(cfg, gameId, color) })
      }
    }

  private def pendingGame(cfg: TournamentBotConfig): Option[(String, String)] =
    for
      detail <- fetchJson(cfg, target(cfg))
      if detail.path("status").asText() == "started"
      round = detail.path("round").asInt(0)
      if round > 0
      pairings <- fetchJson(cfg, target(cfg).path("round").path(round.toString)).map(_.path("pairings"))
      result   <- findBotGame(pairings, cfg.botId)
    yield result

  private def findBotGame(pairings: JsonNode, botId: String): Option[(String, String)] =
    pairings
      .elements()
      .asScala
      .flatMap { p =>
        val whiteId = p.path("white").path("id").asText()
        val blackId = p.path("black").path("id").asText()
        val color   = if whiteId == botId then Some("white") else if blackId == botId then Some("black") else None
        color.flatMap(c => activeMatch(p.path("matches")).map(gameId => (gameId, c)))
      }
      .nextOption()

  private def activeMatch(matches: JsonNode): Option[String] =
    matches
      .elements()
      .asScala
      .find(m => m.path("gameId").asText().nonEmpty && !(m.has("outcome") && !m.path("outcome").isNull))
      .map(_.path("gameId").asText())

  private def fetchJson(cfg: TournamentBotConfig, t: jakarta.ws.rs.client.WebTarget): Option[JsonNode] =
    Try {
      val response = authed(cfg, t).get()
      try
        if response.getStatus == 200 then Some(objectMapper.readTree(response.readEntity(classOf[String])))
        else None
      finally response.close()
    }.getOrElse(None)

  private def resolveAutoJoinToken(): Option[String] =
    autoJoinToken match
      case some @ Some(_) => some
      case None =>
        autoJoinToken = registerWithServer(autoJoinServerUrl, botName(hardestDifficulty))
        autoJoinToken

  private def openTournaments(): List[String] =
    Try {
      val response = client
        .target(autoJoinServerUrl)
        .path("api")
        .path("tournament")
        .request(MediaType.APPLICATION_JSON)
        .get()
      if response.getStatus == 200 then
        val node = objectMapper.readTree(response.readEntity(classOf[String]))
        response.close()
        node.path("created").elements().asScala.toList.map(_.path("id").asText()).filter(_.nonEmpty)
      else { response.close(); Nil }
    }.getOrElse(Nil)

  private def resolveToken(difficulty: String): Option[String] =
    val name     = botName(difficulty)
    val redisKey = s"${redisConfig.prefix}:tournament-bot:token:$name"
    fetchTokenFromAccountService(name)
      .orElse(registerWithServer(tournamentServiceUrl, name))
      .map { token =>
        redis.value(classOf[String]).set(redisKey, token)
        log.infof("Refreshed bot token for %s — stored in Redis", name)
        token
      }
      .orElse {
        Option(redis.value(classOf[String]).get(redisKey)).filter(_.nonEmpty).map { token =>
          log.infof("Using cached bot token for %s from Redis", name)
          token
        }
      }
      .orElse {
        System.getenv().asScala.get("TOURNAMENT_BOT_TOKEN").filter(_.nonEmpty).map { token =>
          log.infof("Using TOURNAMENT_BOT_TOKEN env var for %s", name)
          token
        }
      }

  private def registerWithServer(serverUrl: String, name: String): Option[String] =
    Try {
      val body = s"""{"name":"${name.replace("\"", "\\\"")}","isBot":true}"""
      val response = client
        .target(serverUrl)
        .path("api")
        .path("auth")
        .path("register")
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.entity(body, MediaType.APPLICATION_JSON))
      val status = response.getStatus
      if status == 200 || status == 201 then
        val token = objectMapper.readTree(response.readEntity(classOf[String])).path("token").asText()
        response.close()
        Option(token).filter(_.nonEmpty)
      else
        val errBody = response.readEntity(classOf[String])
        log.warnf("Register %s on %s returned status %d: %s", name, serverUrl, status, errBody)
        response.close()
        None
    }.recover { case ex => log.warnf(ex, "Register %s on %s failed", name, serverUrl); None }.toOption.flatten

  private def fetchTokenFromAccountService(name: String): Option[String] =
    Try(accountServiceClient.getBotToken(name).token).toOption
      .filter(_.nonEmpty)
      .orElse {
        Try {
          val allNames = BotController.listBots.map(botName)
          accountServiceClient.syncBots(de.nowchess.bot.client.SyncOfficialBotsRequest(allNames))
          accountServiceClient.getBotToken(name).token
        }.toOption.filter(_.nonEmpty)
      }

  private def parkOnStartup(token: Option[String]): Unit =
    val localAccountUrl = System.getenv().asScala.getOrElse("ACCOUNT_SERVICE_URL", "http://localhost:8083")
    token match
      case None => log.warn("No bot token resolved — skipping local park")
      case Some(tok) =>
        BotController.listBots.foreach(diff => parkOnAccountService(localAccountUrl, diff, tok))
    fetchRemoteServers().foreach { serverUrl =>
      BotController.listBots.foreach { diff =>
        val name = botName(diff)
        registerWithServer(serverUrl, name) match
          case None      => log.warnf("Could not register %s on %s — skipping park", name, serverUrl)
          case Some(tok) => parkOnTournamentServer(serverUrl, name, tok)
      }
    }

  private def fetchRemoteServers(): List[String] =
    Try {
      val response = client
        .target(tournamentServiceUrl)
        .path("api")
        .path("tournament")
        .path("servers")
        .request(MediaType.APPLICATION_JSON)
        .get()
      if response.getStatus == 200 then
        val node = objectMapper.readTree(response.readEntity(classOf[String]))
        response.close()
        node.path("servers").elements().asScala.toList.map(_.path("url").asText()).filter(_.nonEmpty)
      else { response.close(); Nil }
    }.getOrElse(Nil)

  private def parkOnAccountService(serverUrl: String, difficulty: String, token: String): Unit =
    Try {
      val body = s"""{"name":"${botName(difficulty)}"}"""
      val response = client
        .target(serverUrl)
        .path("api")
        .path("account")
        .path("bots")
        .request(MediaType.APPLICATION_JSON)
        .header("Authorization", s"Bearer $token")
        .post(Entity.entity(body, MediaType.APPLICATION_JSON))
      if response.getStatus == 201 || response.getStatus == 200 then
        val id = objectMapper.readTree(response.readEntity(classOf[String])).path("id").asText()
        log.infof("Parked bot %s on %s as id %s", botName(difficulty), serverUrl, id)
      else log.warnf("Park %s on %s returned status %d", botName(difficulty), serverUrl, response.getStatus)
      response.close()
    }.failed.foreach(ex => log.warnf(ex, "Failed to park %s on %s", botName(difficulty), serverUrl))

  private def parkOnTournamentServer(serverUrl: String, name: String, token: String): Unit =
    Try {
      val body = s"""{"name":"${name.replace("\"", "\\\"")}"}"""
      val response = client
        .target(serverUrl)
        .path("api")
        .path("bots")
        .request(MediaType.APPLICATION_JSON)
        .header("Authorization", s"Bearer $token")
        .post(Entity.entity(body, MediaType.APPLICATION_JSON))
      if response.getStatus == 201 || response.getStatus == 200 then
        val id = objectMapper.readTree(response.readEntity(classOf[String])).path("id").asText()
        log.infof("Parked bot %s on tournament server %s as id %s", name, serverUrl, id)
      else log.warnf("Park %s on tournament server %s returned status %d", name, serverUrl, response.getStatus)
      response.close()
    }.failed.foreach(ex => log.warnf(ex, "Failed to park %s on tournament server %s", name, serverUrl))

  private def botName(difficulty: String): String = s"NowChess ${difficulty.capitalize}"

  def joinTournament(
      tournamentId: String,
      botToken: Option[String],
      difficulty: String,
  ): Either[String, String] =
    val redisKey = s"${redisConfig.prefix}:tournament-bot:token:${botName(difficulty)}"
    val resolvedToken = botToken
      .filter(_.nonEmpty)
      .orElse(Option(redis.value(classOf[String]).get(redisKey)).filter(_.nonEmpty))
      .orElse(resolveToken(difficulty))
    resolvedToken match
      case None => Left("No bot token provided and TOURNAMENT_BOT_TOKEN not configured")
      case Some(token) =>
        TournamentBotConfig.jwtSubject(token) match
          case None => Left("Invalid bot token — could not extract subject")
          case Some(botId) =>
            val cfg = TournamentBotConfig(tournamentServiceUrl, tournamentId, token, botId, difficulty)
            if joinedOrParticipating(cfg) then
              startAsync(cfg)
              Right(botId)
            else Left("Failed to join tournament")

  private def startAsync(cfg: TournamentBotConfig): Unit =
    val thread = new Thread(() => streamLoop(cfg), s"TournamentBot-${cfg.tournamentId}")
    thread.setDaemon(true)
    thread.start()

  @PreDestroy
  def cleanup(): Unit =
    running = false
    workers.shutdownNow()
    Try(client.close())
    log.info("Tournament bot stopped")

  private def streamLoop(cfg: TournamentBotConfig): Unit =
    while running do
      Try(streamEvents(cfg)) match
        case Failure(ex) => log.warnf(ex, "Tournament event stream dropped — reconnecting"); sleep(5000)
        case Success(_)  => sleep(2000)

  // 200 = joined, 409 = already a participant (e.g. after a restart) — both mean "play this tournament".
  private def joinedOrParticipating(cfg: TournamentBotConfig): Boolean =
    Try {
      val response = authed(cfg, target(cfg).path("join"))
        .post(Entity.entity("", MediaType.APPLICATION_JSON))
      val status = response.getStatus
      response.close()
      status match
        case 200 => log.infof("Joined tournament %s", cfg.tournamentId); true
        case 409 => log.infof("Already in tournament %s — resuming", cfg.tournamentId); true
        case other =>
          log.errorf("Failed to join tournament %s — status %d", cfg.tournamentId, other); false
    }.getOrElse { log.error("Join request failed"); false }

  private def streamEvents(cfg: TournamentBotConfig): Unit =
    val response = authed(cfg, target(cfg).path("stream"))
      .header("Accept", "application/x-ndjson")
      .get()
    if response.getStatus != 200 then
      log.warnf("Tournament stream returned status %d", response.getStatus)
      response.close()
      sleep(5000)
    else
      log.infof("Listening to tournament %s event stream", cfg.tournamentId)
      forEachLine(response.readEntity(classOf[InputStream])): line =>
        parse(line).foreach: node =>
          if node.path("type").asText() == "gameStart" then onGameStart(cfg, node.path("gameId").asText())

  private def onGameStart(cfg: TournamentBotConfig, gameId: String): Unit =
    if gameId.isEmpty then ()
    else
      log.infof("gameStart received — tournament=%s game=%s bot=%s", cfg.tournamentId, gameId, cfg.botId)
      resolveColor(cfg, gameId) match
        case None => log.infof("Skipping game %s — bot %s is not a participant", gameId, cfg.botId)
        case Some(color) =>
          if activeGames.add(gameId) then
            log.infof("Joining game %s as %s", gameId, color)
            workers.submit(new Runnable { def run(): Unit = playGame(cfg, gameId, color) })
            ()

  private def resolveColor(cfg: TournamentBotConfig, gameId: String): Option[String] =
    fetchGame(cfg, gameId).flatMap { node =>
      val whiteId = node.path("white").path("id").asText()
      val blackId = node.path("black").path("id").asText()
      if whiteId == cfg.botId then Some("white")
      else if blackId == cfg.botId then Some("black")
      else None
    }

  private def fetchGame(cfg: TournamentBotConfig, gameId: String): Option[JsonNode] =
    Try {
      val response = authed(cfg, target(cfg).path("game").path(gameId)).get()
      try
        if response.getStatus == 200 then Some(objectMapper.readTree(response.readEntity(classOf[String])))
        else { log.warnf("Game detail %s returned status %d", gameId, response.getStatus); None }
      finally response.close()
    }.getOrElse(None)

  private def playGame(cfg: TournamentBotConfig, gameId: String, color: String): Unit =
    Try {
      log.infof("Playing game %s as %s", gameId, color)
      if !streamGameLoop(cfg, gameId, color) then
        log.infof("Stream unavailable for game %s — falling back to polling", gameId)
        pollGameLoop(cfg, gameId, color)
      activeGames.remove(gameId)
    } match
      case Failure(ex) => log.errorf(ex, "Game %s crashed", gameId); activeGames.remove(gameId)
      case Success(_)  => ()

  // Push-based play: the game stream delivers the opponent's move the instant it lands, so our clock
  // is not burned waiting between polls. Heartbeats (every 10s) keep the NDJSON connection flushing.
  // Returns true if the game was driven to completion via the stream; false to fall back to polling.
  private def streamGameLoop(cfg: TournamentBotConfig, gameId: String, color: String): Boolean =
    val myColor = resolveColor(cfg, gameId).getOrElse(color)
    val lastFen = AtomicReference("")
    Try {
      val response = authed(cfg, target(cfg).path("game").path(gameId).path("stream"))
        .header("Accept", "application/x-ndjson")
        .get()
      try
        if response.getStatus != 200 then
          log.warnf("Game stream %s returned status %d", gameId, response.getStatus)
          false
        else
          log.infof("Streaming game %s as %s", gameId, myColor)
          forEachLine(response.readEntity(classOf[InputStream])): line =>
            parse(line).foreach(node => handleStreamEvent(cfg, gameId, myColor, node, lastFen))
          true
      finally response.close()
    } match
      case Success(completed) => completed
      case Failure(ex)        => log.warnf(ex, "Game stream %s failed", gameId); false

  private def handleStreamEvent(
      cfg: TournamentBotConfig,
      gameId: String,
      myColor: String,
      node: JsonNode,
      lastFen: AtomicReference[String],
  ): Unit =
    val eventType = node.path("type").asText()
    if eventType == "move" || eventType == "gameState" then
      val status = node.path("status").asText("ongoing")
      val turn   = node.path("turn").asText()
      val fen    = node.path("fen").asText()
      if !gameTerminalStatuses.contains(status) && turn == myColor && fen.nonEmpty && fen != lastFen.get then
        lastFen.set(fen)
        val time = readTimeControl(node, myColor)
        log.infof("Our turn (stream) in game %s — computing move (fen=%s, budget=%dms)", gameId, fen, time.budgetMs)
        computeUci(cfg, fen, time) match
          case None      => log.warnf("No move found for game %s (fen=%s)", gameId, fen)
          case Some(uci) => submitMove(cfg, gameId, uci)

  // The native JAX-RS client buffers streaming responses, so reading the NDJSON game stream blocks
  // forever. Poll the game state with plain GETs (which work) and move when it is our turn.
  private def pollGameLoop(cfg: TournamentBotConfig, gameId: String, color: String): Unit =
    // scalafix:off DisableSyntax.var
    var done    = false
    var lastFen = ""
    // scalafix:on DisableSyntax.var
    while running && !done do
      fetchJson(cfg, target(cfg).path("game").path(gameId)) match
        case None => sleep(2000)
        case Some(node) =>
          val status = node.path("status").asText()
          if gameTerminalStatuses.contains(status) then
            log.infof("Game %s ended — status=%s", gameId, status); done = true
          else
            // TEMP: tournament-server reports wrong color in pairings (everyone white).
            // The game endpoint white/black ids are correct, so derive our color from it.
            val whiteId = node.path("white").path("id").asText()
            val blackId = node.path("black").path("id").asText()
            val myColor =
              if whiteId == cfg.botId then "white"
              else if blackId == cfg.botId then "black"
              else color
            val turn = node.path("turn").asText()
            val fen  = node.path("fen").asText()
            if turn == myColor && status == "ongoing" && fen.nonEmpty && fen != lastFen then
              lastFen = fen
              val time = readTimeControl(node, myColor)
              log.infof("Our turn in game %s — computing move (fen=%s, budget=%dms)", gameId, fen, time.budgetMs)
              computeUci(cfg, fen, time) match
                case None      => log.warnf("No move found for game %s (fen=%s)", gameId, fen)
                case Some(uci) => submitMove(cfg, gameId, uci)
            sleep(pollIntervalMs)

  // Server clock is reported in seconds; convert to a millisecond TimeControl so the engine can
  // size its move budget against the real clock instead of a fixed guess.
  private def readTimeControl(node: JsonNode, myColor: String): TimeControl =
    val clock = node.path("clock")
    if clock.isMissingNode || clock.isNull then TimeControl.Unlimited
    else
      val field       = if myColor == "white" then "whiteTime" else "blackTime"
      val remainingMs = (clock.path(field).asDouble(0.0) * 1000.0).toLong
      val incrementMs = (clock.path("increment").asDouble(0.0) * 1000.0).toLong
      TimeControl(remainingMs, incrementMs)

  private def computeUci(cfg: TournamentBotConfig, fen: String, time: TimeControl): Option[String] =
    FenParser.parseFen(fen) match
      case Left(err)      => log.warnf("FEN parse failed: %s (%s)", fen, err.toString); None
      case Right(context) => engine(cfg).move(context, time).map(toUci)

  private def submitMove(cfg: TournamentBotConfig, gameId: String, uci: String): Unit =
    Try {
      val response = authed(cfg, target(cfg).path("game").path(gameId).path("move").path(uci))
        .post(Entity.entity("", MediaType.APPLICATION_JSON))
      if response.getStatus == 200 then log.infof("Played %s in game %s", uci, gameId)
      else log.warnf("Move %s rejected in game %s — status %d", uci, gameId, response.getStatus)
      response.close()
    } match
      case Failure(ex) => log.errorf(ex, "Error submitting move %s in game %s", uci, gameId)
      case Success(_)  => ()

  private def engine(cfg: TournamentBotConfig): Bot =
    botController.getBot(cfg.difficulty).orElse(botController.getBot("medium")).get

  private def target(cfg: TournamentBotConfig) =
    client.target(cfg.serverUrl).path("api").path("tournament").path(cfg.tournamentId)

  private def authed(cfg: TournamentBotConfig, t: jakarta.ws.rs.client.WebTarget) =
    t.request(MediaType.APPLICATION_JSON).header("Authorization", s"Bearer ${cfg.token}")

  private def parse(line: String): Option[JsonNode] =
    val trimmed = line.trim
    if trimmed.isEmpty then None else Try(objectMapper.readTree(trimmed)).toOption

  private def forEachLine(stream: InputStream)(handle: String => Unit): Unit =
    val reader = new BufferedReader(new InputStreamReader(stream))
    Iterator
      .continually(reader.readLine())
      .map(Option(_))
      .takeWhile(opt => opt.isDefined && running)
      .flatten
      .foreach { line =>
        Try(handle(line)).failed.foreach(ex => log.warnf(ex, "Error handling stream line"))
      }

  private def toUci(move: Move): String =
    val base = s"${move.from}${move.to}"
    move.moveType match
      case MoveType.Promotion(piece) => base + promotionChar(piece)
      case _                         => base

  private def promotionChar(piece: PromotionPiece): String =
    piece match
      case PromotionPiece.Knight => "n"
      case PromotionPiece.Bishop => "b"
      case PromotionPiece.Rook   => "r"
      case PromotionPiece.Queen  => "q"

  private def sleep(ms: Long): Unit = Try(Thread.sleep(ms))
