package de.nowchess.chess.redis

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.board.Color
import de.nowchess.api.dto.GameFullEventDto
import de.nowchess.api.game.GameMode
import de.nowchess.chess.config.RedisConfig
import de.nowchess.chess.grpc.IoGrpcClientWrapper
import de.nowchess.chess.observer.Observer
import de.nowchess.chess.registry.GameRegistry
import de.nowchess.chess.resource.GameDtoMapper
import de.nowchess.chess.service.InstanceHeartbeatService
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.pubsub.PubSubCommands
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import scala.compiletime.uninitialized
import scala.util.Try
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

@ApplicationScoped
class GameRedisSubscriberManager:

  // scalafix:off DisableSyntax.var
  @Inject var redis: RedisDataSource                                       = uninitialized
  @Inject var registry: GameRegistry                                       = uninitialized
  @Inject var objectMapper: ObjectMapper                                   = uninitialized
  @Inject var redisConfig: RedisConfig                                     = uninitialized
  @Inject var ioClient: IoGrpcClientWrapper                                = uninitialized
  @Inject var heartbeatServiceInstance: Instance[InstanceHeartbeatService] = uninitialized
  // scalafix:on DisableSyntax.var

  private def heartbeatServiceOpt: Option[InstanceHeartbeatService] =
    if heartbeatServiceInstance.isUnsatisfied then None
    else Some(heartbeatServiceInstance.get())

  private val c2sListeners = new ConcurrentHashMap[String, PubSubCommands.RedisSubscriber]()
  private val s2cObservers = new ConcurrentHashMap[String, Observer]()

  private def c2sTopic(gameId: String): String =
    s"${redisConfig.prefix}:game:$gameId:c2s"

  private def s2cTopicName(gameId: String): String =
    s"${redisConfig.prefix}:game:$gameId:s2c"

  def subscribeGame(gameId: String): Unit =
    try
      val handler: Consumer[String] = msg => handleC2sMessage(gameId, msg)
      val subscriber                = redis.pubsub(classOf[String]).subscribe(c2sTopic(gameId), handler)
      c2sListeners.put(gameId, subscriber)

      val writebackFn: String => Unit = json => redis.pubsub(classOf[String]).publish("game-writeback", json)
      val obs = new GameRedisPublisher(
        gameId,
        registry,
        redis,
        objectMapper,
        s2cTopicName(gameId),
        writebackFn,
        ioClient,
        unsubscribeGame,
      )
      s2cObservers.put(gameId, obs)
      registry.get(gameId).foreach(_.engine.subscribe(obs))

      heartbeatServiceOpt.foreach(_.addGameSubscription(gameId))
    catch
      case e: Exception =>
        System.err.println(s"Warning: Redis subscription failed for game $gameId: ${e.getMessage}")
        ()

  def unsubscribeGame(gameId: String): Unit =
    Option(c2sListeners.remove(gameId)).foreach { subscriber =>
      subscriber.unsubscribe(c2sTopic(gameId))
    }
    Option(s2cObservers.remove(gameId)).foreach { obs =>
      registry.get(gameId).foreach(_.engine.unsubscribe(obs))
    }

    heartbeatServiceOpt.foreach(_.removeGameSubscription(gameId))

  private def handleC2sMessage(gameId: String, msg: String): Unit =
    parseC2sMessage(msg) match
      case Some(C2sMessage.Connected)           => handleConnected(gameId)
      case Some(C2sMessage.Move(uci, playerId)) => handleMove(gameId, uci, playerId)
      case Some(C2sMessage.Ping)                => ()
      case None                                 => ()

  private def handleConnected(gameId: String): Unit =
    registry.get(gameId).foreach { entry =>
      val dto  = GameDtoMapper.toGameFullDto(entry, ioClient)
      val json = objectMapper.writeValueAsString(GameFullEventDto(dto))
      redis.pubsub(classOf[String]).publish(s2cTopicName(gameId), json)
    }

  private def handleMove(gameId: String, uci: String, playerId: Option[String]): Unit =
    registry.get(gameId).foreach { entry =>
      entry.mode match
        case GameMode.Open => entry.engine.processUserInput(uci)
        case GameMode.Authenticated =>
          playerId match
            case None => ()
            case Some(pid) =>
              val turn = entry.engine.context.turn
              val authorised =
                (entry.white.id.value == pid && turn == Color.White) ||
                  (entry.black.id.value == pid && turn == Color.Black)
              if authorised then entry.engine.processUserInput(uci)
    }

  private def parseC2sMessage(msg: String): Option[C2sMessage] =
    Try(objectMapper.readTree(msg)).toOption.flatMap { node =>
      Option(node.get("type")).map(_.asText()).flatMap {
        case "CONNECTED" => Some(C2sMessage.Connected)
        case "MOVE" =>
          Option(node.get("uci")).map { u =>
            val pid = Option(node.get("playerId")).map(_.asText()).filter(_.nonEmpty)
            C2sMessage.Move(u.asText(), pid)
          }
        case "PING" => Some(C2sMessage.Ping)
        case _      => None
      }
    }

  def batchResubscribeGames(gameIds: java.util.List[String]): Int =
    gameIds.forEach(subscribeGame)
    gameIds.size()

  def unsubscribeGames(gameIds: java.util.List[String]): Int =
    gameIds.forEach(unsubscribeGame)
    gameIds.size()

  def evictGames(gameIds: java.util.List[String]): Int =
    gameIds.forEach(unsubscribeGame)
    gameIds.size()

  def drainInstance(): Int =
    val gameIds = new java.util.ArrayList(c2sListeners.keySet())
    val count   = gameIds.size()
    gameIds.forEach(unsubscribeGame)
    count

  @PreDestroy
  def cleanup(): Unit =
    c2sListeners.forEach((gameId, subscriber) => subscriber.unsubscribe(c2sTopic(gameId)))
    s2cObservers.forEach((gameId, obs) => registry.get(gameId).foreach(_.engine.unsubscribe(obs)))
