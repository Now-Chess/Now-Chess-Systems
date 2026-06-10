package de.nowchess.ws.resource

import de.nowchess.ws.config.RedisConfig
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.stream.{XAddArgs, XGroupCreateArgs, XReadGroupArgs}
import io.quarkus.websockets.next.*
import io.smallrye.jwt.auth.principal.JWTParser
import jakarta.inject.Inject
import org.eclipse.microprofile.context.ManagedExecutor
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@WebSocket(path = "/api/user/ws")
class UserWebSocketResource:

  private val log = Logger.getLogger(classOf[UserWebSocketResource])

  // scalafix:off DisableSyntax.var
  @Inject var redis: RedisDataSource    = uninitialized
  @Inject var redisConfig: RedisConfig  = uninitialized
  @Inject var jwtParser: JWTParser      = uninitialized
  @Inject var executor: ManagedExecutor = uninitialized
  // scalafix:on DisableSyntax.var

  private val consumerId   = UUID.randomUUID().toString
  private val maxRetries   = 3
  private val maxStreamLen = 1000L

  private val connections = new ConcurrentHashMap[String, (String, WebSocketConnection)]()

  private def userStreamKey(userId: String): String =
    s"${redisConfig.prefix}:user:$userId:events:stream"

  private def dlqKey: String = s"${redisConfig.prefix}:dlq"

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
        log.infof("User WebSocket opened — userId=%s connId=%s", userId, connection.id())
        createGroupIfAbsent(userId, connection.id())
        connections.put(connection.id(), (userId, connection))
        executor.submit(
          new Runnable:
            def run(): Unit = pollLoop(connection.id(), userId, connection),
        )
        val connectedMsg = s"""{"type":"CONNECTED","userId":"$userId"}"""
        connection.sendText(connectedMsg).subscribe().`with`(_ => (), _ => ())

  @OnClose
  def onClose(connection: WebSocketConnection): Unit =
    log.infof("User WebSocket closed — connectionId=%s", connection.id())
    val userIdOpt = Option(connections.remove(connection.id())).map(_._1)
    userIdOpt.foreach { userId =>
      Try(redis.stream(classOf[String]).xgroupDestroy(userStreamKey(userId), connection.id())) match
        case Failure(ex) => log.warnf(ex, "Failed to destroy consumer group for connectionId=%s", connection.id())
        case Success(_)  => ()
    }

  private def createGroupIfAbsent(userId: String, groupName: String): Unit =
    Try(
      redis
        .stream(classOf[String])
        .xgroupCreate(userStreamKey(userId), groupName, "$", new XGroupCreateArgs().mkstream()),
    ) match
      case Failure(ex) if Option(ex.getMessage).exists(_.contains("BUSYGROUP")) => ()
      case Failure(ex) => log.warnf(ex, "Failed to create consumer group for userId=%s", userId)
      case Success(_)  => ()

  private def pollLoop(connectionId: String, userId: String, myConnection: WebSocketConnection): Unit =
    while Option(connections.get(connectionId)).exists(_._2 eq myConnection) do
      Try {
        val messages = redis
          .stream(classOf[String])
          .xreadgroup(
            connectionId,
            consumerId,
            userStreamKey(userId),
            ">",
            new XReadGroupArgs().count(10).block(Duration.ofSeconds(2)),
          )
        Option(messages).foreach(_.forEach { msg =>
          if Option(connections.get(connectionId)).exists(_._2 eq myConnection) then
            val json    = msg.payload().get("data")
            val attempt = Option(msg.payload().get("attempt")).flatMap(_.toIntOption).getOrElse(0)
            Try(myConnection.sendText(json).await().atMost(Duration.ofSeconds(5))) match
              case Success(_) =>
                ack(connectionId, userId, msg.id())
              case Failure(_) if attempt + 1 < maxRetries =>
                xadd(userStreamKey(userId), json, attempt + 1)
                ack(connectionId, userId, msg.id())
              case Failure(ex) =>
                log.warnf(ex, "Delivery failed for userId=%s after %d attempts, sending to DLQ", userId, maxRetries)
                xadd(dlqKey, json, attempt)
                ack(connectionId, userId, msg.id())
        })
      } match
        case Failure(ex) => log.warnf(ex, "Error in poll loop for userId=%s", userId)
        case Success(_)  => ()

  private def ack(groupName: String, userId: String, id: String): Unit =
    Try(redis.stream(classOf[String]).xack(userStreamKey(userId), groupName, id)) match
      case Failure(ex) => log.warnf(ex, "Failed to ack message %s for userId=%s", id, userId)
      case Success(_)  => ()

  private def xadd(key: String, json: String, attempt: Int): Unit =
    Try(
      redis
        .stream(classOf[String])
        .xadd(
          key,
          new XAddArgs().maxlen(maxStreamLen).nearlyExactTrimming(),
          Map("data" -> json, "attempt" -> attempt.toString).asJava,
        ),
    ) match
      case Failure(ex) => log.warnf(ex, "Failed to publish to stream %s", key)
      case Success(_)  => ()
