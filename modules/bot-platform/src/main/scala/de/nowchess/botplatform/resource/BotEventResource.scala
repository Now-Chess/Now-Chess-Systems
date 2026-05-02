package de.nowchess.botplatform.resource

import de.nowchess.botplatform.config.RedisConfig
import de.nowchess.botplatform.registry.BotRegistry
import io.quarkus.redis.datasource.RedisDataSource
import io.smallrye.mutiny.Multi
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}
import org.eclipse.microprofile.jwt.JsonWebToken
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import java.util.function.Consumer

@Path("/api/bot")
@ApplicationScoped
@RolesAllowed(Array("**"))
class BotEventResource:

  private val log = Logger.getLogger(classOf[BotEventResource])

  // scalafix:off DisableSyntax.var
  @Inject var registry: BotRegistry    = uninitialized
  @Inject var jwt: JsonWebToken        = uninitialized
  @Inject var redis: RedisDataSource   = uninitialized
  @Inject var redisConfig: RedisConfig = uninitialized
  // scalafix:on DisableSyntax.var

  @GET
  @Path("/stream/events")
  @Produces(Array(MediaType.SERVER_SENT_EVENTS))
  def streamEvents(@QueryParam("botId") botId: String): Multi[String] =
    val tokenType = Option(jwt.getClaim[AnyRef]("type")).map(_.toString).getOrElse("")
    val subject   = Option(jwt.getSubject).getOrElse("")
    if tokenType != "bot" || subject != botId then
      log.warnf("Unauthorized bot stream access — tokenType=%s subject=%s botId=%s", tokenType, subject, botId)
      Multi.createFrom().failure(new ForbiddenException("Not authorized for this bot"))
    else
      log.infof("Bot %s connected to event stream", botId)
      Multi.createFrom().emitter[String] { emitter =>
        registry.register(botId, emitter)
        emitter.onTermination(() => registry.unregister(botId))
      }

  @GET
  @Path("/game/stream/{gameId}")
  @Produces(Array(MediaType.SERVER_SENT_EVENTS))
  def streamGame(@PathParam("gameId") gameId: String): Multi[String] =
    Multi.createFrom().emitter[String] { emitter =>
      val topicName                 = s"${redisConfig.prefix}:game:$gameId:s2c"
      val handler: Consumer[String] = msg => emitter.emit(msg)
      val subscriber                = redis.pubsub(classOf[String]).subscribe(topicName, handler)
      emitter.onTermination(() => subscriber.unsubscribe(topicName))
    }

  @POST
  @Path("/game/{gameId}/move/{uci}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def makeMove(
      @PathParam("gameId") gameId: String,
      @PathParam("uci") uci: String,
  ): Response =
    val playerId = Option(jwt.getSubject).getOrElse("")
    log.debugf("Bot move %s in game %s by player %s", uci, gameId, playerId)
    val moveMsg = s"""{"type":"MOVE","uci":"$uci","playerId":"$playerId"}"""
    redis.pubsub(classOf[String]).publish(s"${redisConfig.prefix}:game:$gameId:c2s", moveMsg)
    Response.ok().build()
