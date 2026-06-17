package de.nowchess.bot.service

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.bot.{Bot, BotController}
import de.nowchess.io.fen.FenParser
import io.quarkus.runtime.Startup
import jakarta.annotation.{PostConstruct, PreDestroy}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.client.{Client, ClientBuilder, Entity}
import jakarta.ws.rs.core.MediaType
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}
import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.util.concurrent.{ConcurrentHashMap, ExecutorService, Executors}

@Startup
@ApplicationScoped
class TournamentBotGamePlayer:

  private val log = Logger.getLogger(classOf[TournamentBotGamePlayer])

  // scalafix:off DisableSyntax.var
  @Inject var objectMapper: ObjectMapper   = uninitialized
  @Inject var botController: BotController = uninitialized
  // scalafix:on DisableSyntax.var

  private val client: Client           = ClientBuilder.newClient()
  private val workers: ExecutorService = Executors.newCachedThreadPool()
  private val activeGames              = ConcurrentHashMap.newKeySet[String]()

  private val config = TournamentBotConfig.fromEnv(System.getenv().asScala.toMap)

  // scalafix:off DisableSyntax.var
  @volatile private var running = true
  // scalafix:on DisableSyntax.var

  val defaultServerUrl: String =
    System.getenv().asScala.getOrElse("TOURNAMENT_SERVER_URL", "http://localhost:8089")

  @PostConstruct
  def initialize(): Unit =
    config match
      case None =>
        log.info("Tournament bot disabled — set TOURNAMENT_ID and TOURNAMENT_BOT_TOKEN to enable")
      case Some(cfg) =>
        log.infof("Tournament bot enabled — server=%s tournament=%s bot=%s", cfg.serverUrl, cfg.tournamentId, cfg.botId)
        startAsync(cfg)

  def joinTournament(
      tournamentId: String,
      botToken: String,
      difficulty: String,
      serverUrl: String,
  ): Either[String, String] =
    TournamentBotConfig.jwtSubject(botToken) match
      case None => Left("Invalid bot token — could not extract subject")
      case Some(botId) =>
        val cfg = TournamentBotConfig(serverUrl, tournamentId, botToken, botId, difficulty)
        if join(cfg) then
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

  private def join(cfg: TournamentBotConfig): Boolean =
    Try {
      val response = authed(cfg, target(cfg).path("join"))
        .post(Entity.entity("", MediaType.APPLICATION_JSON))
      val ok = response.getStatus == 200
      if ok then log.infof("Joined tournament %s", cfg.tournamentId)
      else log.errorf("Failed to join tournament %s — status %d", cfg.tournamentId, response.getStatus)
      response.close()
      ok
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
          if node.path("type").asText() == "gameStart" then
            onGameStart(cfg, node.path("gameId").asText(), node.path("color").asText())

  private def onGameStart(cfg: TournamentBotConfig, gameId: String, color: String): Unit =
    if gameId.nonEmpty && color.nonEmpty && activeGames.add(gameId) then
      workers.submit(new Runnable { def run(): Unit = playGame(cfg, gameId, color) })
      ()

  private def playGame(cfg: TournamentBotConfig, gameId: String, color: String): Unit =
    Try {
      log.infof("Playing game %s as %s", gameId, color)
      openGameStream(cfg, gameId).foreach(consumeGameStream(cfg, gameId, color, _))
      activeGames.remove(gameId)
    } match
      case Failure(ex) => log.errorf(ex, "Game %s crashed", gameId); activeGames.remove(gameId)
      case Success(_)  => ()

  private def consumeGameStream(cfg: TournamentBotConfig, gameId: String, color: String, stream: InputStream): Unit =
    val reader = new BufferedReader(new InputStreamReader(stream))
    // scalafix:off DisableSyntax.var
    var done = false
    // scalafix:on DisableSyntax.var
    Iterator
      .continually(reader.readLine())
      .map(Option(_))
      .takeWhile(opt => opt.isDefined && running && !done)
      .flatten
      .foreach { line =>
        parse(line).foreach: node =>
          node.path("type").asText() match
            case "gameState" =>
              maybeMove(
                cfg,
                gameId,
                color,
                node.path("turn").asText(),
                node.path("status").asText(),
                node.path("fen").asText(),
              )
            case "move" =>
              maybeMove(cfg, gameId, color, node.path("turn").asText(), "ongoing", node.path("fen").asText())
            case "gameEnd" =>
              log.infof(
                "Game %s ended — status=%s winner=%s",
                gameId,
                node.path("status").asText(),
                node.path("winner").asText(),
              ); done = true
            case _ => ()
      }

  private def maybeMove(
      cfg: TournamentBotConfig,
      gameId: String,
      color: String,
      turn: String,
      status: String,
      fen: String,
  ): Unit =
    if turn == color && status == "ongoing" && fen.nonEmpty then
      computeUci(cfg, fen) match
        case None      => log.warnf("No move found for game %s (fen=%s)", gameId, fen)
        case Some(uci) => submitMove(cfg, gameId, uci)

  private def computeUci(cfg: TournamentBotConfig, fen: String): Option[String] =
    FenParser.parseFen(fen) match
      case Left(err)      => log.warnf("FEN parse failed: %s (%s)", fen, err.toString); None
      case Right(context) => engine(cfg).apply(context).map(toUci)

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

  private def openGameStream(cfg: TournamentBotConfig, gameId: String): Option[InputStream] =
    Try {
      val response = authed(cfg, target(cfg).path("game").path(gameId).path("stream"))
        .header("Accept", "application/x-ndjson")
        .get()
      if response.getStatus == 200 then Some(response.readEntity(classOf[InputStream]))
      else { log.warnf("Game stream %s returned status %d", gameId, response.getStatus); response.close(); None }
    }.getOrElse(None)

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
