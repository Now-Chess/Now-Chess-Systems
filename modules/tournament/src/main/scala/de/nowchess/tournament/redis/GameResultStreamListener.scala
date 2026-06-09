package de.nowchess.tournament.redis

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.dto.GameWritebackEventDto
import de.nowchess.tournament.config.RedisConfig
import de.nowchess.tournament.service.TournamentService
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.stream.{StreamMessage, XGroupCreateArgs, XReadGroupArgs}
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.context.ManagedExecutor
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}
import java.util.UUID

@Startup
@ApplicationScoped
class GameResultStreamListener:
  // scalafix:off DisableSyntax.var
  @Inject var redis: RedisDataSource               = uninitialized
  @Inject var objectMapper: ObjectMapper           = uninitialized
  @Inject var tournamentService: TournamentService = uninitialized
  @Inject var executor: ManagedExecutor            = uninitialized
  @Inject var redisConfig: RedisConfig             = uninitialized
  // scalafix:on

  private val log        = Logger.getLogger(classOf[GameResultStreamListener])
  private val groupName  = "tournament-result"
  private val consumerId = UUID.randomUUID().toString

  private def streamKey = s"${redisConfig.prefix}:game-writeback"

  @PostConstruct
  def startListening(): Unit =
    createGroupIfAbsent()
    executor.submit(
      new Runnable:
        def run(): Unit = pollLoop(),
    )
    log.infof("Tournament result listener started (consumer=%s)", consumerId)

  private def createGroupIfAbsent(): Unit =
    Try(redis.stream(classOf[String]).xgroupCreate(streamKey, groupName, "0", new XGroupCreateArgs().mkstream())) match
      case Failure(ex) if Option(ex.getMessage).exists(_.contains("BUSYGROUP")) => ()
      case Failure(ex) => log.warnf(ex, "Failed to create consumer group")
      case Success(_)  => ()

  private def pollLoop(): Unit =
    // scalafix:off DisableSyntax.var
    var running = true
    // scalafix:on DisableSyntax.var
    while running do
      Try {
        val messages = redis
          .stream(classOf[String])
          .xreadgroup(
            groupName,
            consumerId,
            streamKey,
            ">",
            new XReadGroupArgs().count(10).block(java.time.Duration.ofSeconds(2)),
          )
        Option(messages).foreach(_.forEach(msg => handleMessage(msg)))
      } match
        case Failure(ex) if isInterrupted(ex) =>
          Thread.currentThread().interrupt()
          running = false
        case Failure(ex) => log.warnf(ex, "Error in result poll loop")
        case Success(_)  => ()

  private def isInterrupted(ex: Throwable): Boolean =
    ex match
      case _: InterruptedException => true
      case _ =>
        Option(ex.getCause) match
          case Some(_: InterruptedException) => true
          case _                             => false

  private def handleMessage(msg: StreamMessage[String, String, String]): Unit =
    val json = msg.payload().get("data")
    Try(objectMapper.readValue(json, classOf[GameWritebackEventDto])) match
      case Failure(ex) =>
        log.errorf(ex, "Unparseable game result event: %s", json)
        ack(msg.id())
      case Success(event) =>
        if event.result.isDefined then
          Try(tournamentService.handleGameResult(event.gameId, event.result.get, event.pgn)) match
            case Failure(ex) => log.errorf(ex, "Failed to handle game result for %s", event.gameId)
            case Success(_)  => ()
        ack(msg.id())

  private def ack(id: String): Unit =
    Try(redis.stream(classOf[String]).xack(streamKey, groupName, id)) match
      case Failure(ex) => log.warnf(ex, "Failed to ack message %s", id)
      case Success(_)  => ()
