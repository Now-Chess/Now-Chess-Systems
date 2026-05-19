package de.nowchess.store.redis

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.dto.GameWritebackEventDto
import de.nowchess.store.service.GameWritebackService
import io.quarkus.redis.datasource.RedisDataSource
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.util.{Failure, Success, Try}
import java.util.function.Consumer

@ApplicationScoped
class GameWritebackStreamListener:
  @Inject
  // scalafix:off DisableSyntax.var
  var redis: RedisDataSource                         = uninitialized
  @Inject var objectMapper: ObjectMapper             = uninitialized
  @Inject var writebackService: GameWritebackService = uninitialized
  // scalafix:on

  private val log = Logger.getLogger(classOf[GameWritebackStreamListener])

  @PostConstruct
  def startListening(): Unit =
    val handler: Consumer[String] = json =>
      Try(objectMapper.readValue(json, classOf[GameWritebackEventDto])) match
        case Failure(ex) =>
          log.errorf(ex, "Failed to parse game-writeback event: %s", json)
        case Success(event) =>
          Try(writebackService.writeBack(event)) match
            case Failure(ex) =>
              log.errorf(ex, "Failed to write back game event for gameId=%s", event.gameId)
            case Success(_) => ()
    redis.pubsub(classOf[String]).subscribe("game-writeback", handler)
    log.infof("Started listening to Writebacks")
    ()
