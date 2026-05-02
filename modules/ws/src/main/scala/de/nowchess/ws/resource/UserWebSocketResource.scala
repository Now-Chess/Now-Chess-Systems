package de.nowchess.ws.resource

import de.nowchess.ws.config.RedisConfig
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.pubsub.PubSubCommands
import io.quarkus.websockets.next.*
import io.smallrye.jwt.auth.principal.JWTParser
import jakarta.inject.Inject
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.util.Try
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

@WebSocket(path = "/api/user/ws")
class UserWebSocketResource:

  private val log = Logger.getLogger(classOf[UserWebSocketResource])

  // scalafix:off DisableSyntax.var
  @Inject
  var redis: RedisDataSource = uninitialized

  @Inject
  var redisConfig: RedisConfig = uninitialized

  @Inject
  var jwtParser: JWTParser = uninitialized
  // scalafix:on DisableSyntax.var

  private val connections = new ConcurrentHashMap[String, (String, PubSubCommands.RedisSubscriber)]()

  private def userTopic(userId: String): String =
    s"${redisConfig.prefix}:user:$userId:events"

  @OnOpen
  def onOpen(connection: WebSocketConnection, handshake: HandshakeRequest): Unit =
    val userIdOpt = Option(handshake.header("Authorization"))
      .filter(_.nonEmpty)
      .flatMap(token => Try(jwtParser.parse(token)).toOption)
      .map(_.getSubject)

    userIdOpt match
      case None =>
        log.warn("WebSocket opened with no valid JWT — closing connection")
        connection.close().subscribe().`with`(_ => (), _ => ())
      case Some(userId) =>
        log.infof("User WebSocket opened — userId=%s", userId)
        val handler: Consumer[String] = msg => connection.sendText(msg).subscribe().`with`(_ => (), _ => ())
        val subscriber                = redis.pubsub(classOf[String]).subscribe(userTopic(userId), handler)
        connections.put(connection.id(), (userId, subscriber))
        val connectedMsg = s"""{"type":"CONNECTED","userId":"$userId"}"""
        connection.sendText(connectedMsg).subscribe().`with`(_ => (), _ => ())

  @OnClose
  def onClose(connection: WebSocketConnection): Unit =
    log.infof("User WebSocket closed — connectionId=%s", connection.id())
    Option(connections.remove(connection.id())).foreach { (userId, subscriber) =>
      subscriber.unsubscribe(userTopic(userId))
    }
