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

@WebSocket(path = "/api/board/game/{gameId}/ws")
class GameWebSocketResource:

  private val log = Logger.getLogger(classOf[GameWebSocketResource])

  // scalafix:off DisableSyntax.var
  @Inject
  var redis: RedisDataSource = uninitialized

  @Inject
  var redisConfig: RedisConfig = uninitialized

  @Inject
  var jwtParser: JWTParser = uninitialized
  // scalafix:on DisableSyntax.var

  private val connections = new ConcurrentHashMap[String, ConnectionMeta]()

  private def s2cTopic(gameId: String): String =
    s"${redisConfig.prefix}:game:$gameId:s2c"

  private def c2sTopic(gameId: String): String =
    s"${redisConfig.prefix}:game:$gameId:c2s"

  @OnOpen
  def onOpen(connection: WebSocketConnection, handshake: HandshakeRequest): Unit =
    val gameId = connection.pathParam("gameId")
    val playerId = Option(handshake.header("Authorization"))
      .filter(_.nonEmpty)
      .flatMap(token => Try(jwtParser.parse(token)).toOption)
      .map(_.getSubject)
    log.infof("Game WebSocket opened — gameId=%s playerId=%s", gameId, playerId.getOrElse("anonymous"))
    val handler: Consumer[String] = msg => connection.sendText(msg).subscribe().`with`(_ => (), _ => ())
    val subscriber                = redis.pubsub(classOf[String]).subscribe(s2cTopic(gameId), handler)
    connections.put(connection.id(), ConnectionMeta(gameId, subscriber, playerId))
    val connectedMsg = playerId match
      case Some(pid) => s"""{"type":"CONNECTED","gameId":"$gameId","playerId":"$pid"}"""
      case None      => s"""{"type":"CONNECTED","gameId":"$gameId"}"""
    redis.pubsub(classOf[String]).publish(c2sTopic(gameId), connectedMsg)

  @OnTextMessage
  def onTextMessage(connection: WebSocketConnection, message: String): Unit =
    Option(connections.get(connection.id())).foreach { meta =>
      val enriched = meta.playerId match
        case Some(pid) => injectPlayerId(message, pid)
        case None      => message
      redis.pubsub(classOf[String]).publish(c2sTopic(meta.gameId), enriched)
    }

  @OnClose
  def onClose(connection: WebSocketConnection): Unit =
    Option(connections.remove(connection.id())).foreach { meta =>
      log.infof("Game WebSocket closed — gameId=%s", meta.gameId)
      meta.subscriber.unsubscribe(s2cTopic(meta.gameId))
    }

  private def injectPlayerId(msg: String, pid: String): String =
    val trimmed = msg.trim
    if trimmed.endsWith("}") then trimmed.dropRight(1) + s""","playerId":"$pid"}"""
    else msg
