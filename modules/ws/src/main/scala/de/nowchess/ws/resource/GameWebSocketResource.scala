package de.nowchess.ws.resource

import de.nowchess.ws.config.RedisConfig
import io.micrometer.core.instrument.{Counter, Gauge, MeterRegistry}
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.websockets.next.*
import io.smallrye.jwt.auth.principal.JWTParser
import jakarta.annotation.PostConstruct
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

  @Inject
  var meterRegistry: MeterRegistry = uninitialized
  // scalafix:on DisableSyntax.var

  private val connections = new ConcurrentHashMap[String, ConnectionMeta]()
  private val pendingAuth = ConcurrentHashMap.newKeySet[String]()

  @PostConstruct
  def initializeMetrics(): Unit = {
    connectionsOpened
    connectionsClosed
    messagesReceived
    activeGauge
  }

  private lazy val connectionsOpened: Counter =
    meterRegistry.counter("nowchess.ws.connections.opened")

  private lazy val connectionsClosed: Counter =
    meterRegistry.counter("nowchess.ws.connections.closed")

  private lazy val messagesReceived: Counter =
    meterRegistry.counter("nowchess.ws.messages.received")

  private lazy val activeGauge: Unit =
    Gauge
      .builder("nowchess.ws.connections.active", connections, _.size().toDouble)
      .register(meterRegistry)

  private def s2cTopic(gameId: String): String =
    s"${redisConfig.prefix}:game:$gameId:s2c"

  private def c2sTopic(gameId: String): String =
    s"${redisConfig.prefix}:game:$gameId:c2s"

  @OnOpen
  def onOpen(connection: WebSocketConnection): Unit =
    activeGauge
    val gameId                    = connection.pathParam("gameId")
    val handler: Consumer[String] = msg => connection.sendText(msg).subscribe().`with`(_ => (), _ => ())
    val subscriber                = redis.pubsub(classOf[String]).subscribe(s2cTopic(gameId), handler)
    connections.put(connection.id(), ConnectionMeta(gameId, subscriber, None))
    connectionsOpened.increment()
    pendingAuth.add(connection.id())
    log.infof("Game WebSocket opened — gameId=%s connId=%s awaiting auth", gameId, connection.id())

  @OnTextMessage
  def onTextMessage(connection: WebSocketConnection, message: String): Unit =
    messagesReceived.increment()
    if pendingAuth.remove(connection.id()) then
      val playerIdOpt =
        parseAuthToken(message)
          .flatMap(token => Try(jwtParser.parse(token)).toOption)
          .map(_.getSubject)
      playerIdOpt match
        case None =>
          log.warnf("Game WebSocket auth failed — closing connId=%s", connection.id())
          connection.close().subscribe().`with`(_ => (), _ => ())
        case Some(playerId) =>
          Option(connections.get(connection.id())).foreach { meta =>
            connections.put(connection.id(), meta.copy(playerId = Some(playerId)))
            publishConnected(meta.gameId, Some(playerId))
          }
    else
      Option(connections.get(connection.id())).foreach { meta =>
        val enriched = meta.playerId match
          case Some(pid) => injectPlayerId(message, pid)
          case None      => message
        redis.pubsub(classOf[String]).publish(c2sTopic(meta.gameId), enriched)
      }

  @OnClose
  def onClose(connection: WebSocketConnection): Unit =
    pendingAuth.remove(connection.id())
    Option(connections.remove(connection.id())).foreach { meta =>
      log.infof("Game WebSocket closed — gameId=%s", meta.gameId)
      meta.subscriber.unsubscribe(s2cTopic(meta.gameId))
      connectionsClosed.increment()
    }

  private def parseAuthToken(message: String): Option[String] =
    val trimmed = message.trim
    if !trimmed.contains("\"type\":\"auth\"") then None
    else
      val start = trimmed.indexOf("\"token\":\"")
      if start < 0 then None
      else
        val valueStart = start + 9
        val end        = trimmed.indexOf('"', valueStart)
        if end < 0 then None else Some(trimmed.substring(valueStart, end)).filter(_.nonEmpty)

  private def publishConnected(gameId: String, playerId: Option[String]): Unit =
    val connectedMsg = playerId match
      case Some(pid) => s"""{"type":"CONNECTED","gameId":"$gameId","playerId":"$pid"}"""
      case None      => s"""{"type":"CONNECTED","gameId":"$gameId"}"""
    redis.pubsub(classOf[String]).publish(c2sTopic(gameId), connectedMsg)

  private def injectPlayerId(msg: String, pid: String): String =
    val trimmed = msg.trim
    if trimmed.endsWith("}") then trimmed.dropRight(1) + s""","playerId":"$pid"}"""
    else msg
