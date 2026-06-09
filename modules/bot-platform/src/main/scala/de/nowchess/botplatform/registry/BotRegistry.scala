package de.nowchess.botplatform.registry

import de.nowchess.botplatform.config.RedisConfig
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.stream.{XGroupCreateArgs, XReadGroupArgs}
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.context.ManagedExecutor
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class BotRegistry:

  private val log = Logger.getLogger(classOf[BotRegistry])

  // scalafix:off DisableSyntax.var
  @Inject var redis: RedisDataSource    = uninitialized
  @Inject var redisConfig: RedisConfig  = uninitialized
  @Inject var executor: ManagedExecutor = uninitialized
  // scalafix:on DisableSyntax.var

  private val groupName  = "bot-platform-consumer"
  private val consumerId = UUID.randomUUID().toString

  private val emitters = ConcurrentHashMap[String, MultiEmitter[? >: String]]()

  def register(botId: String, emitter: MultiEmitter[? >: String]): Unit =
    createGroupIfAbsent(botId)
    emitters.put(botId, emitter)
    executor.submit(
      new Runnable:
        def run(): Unit = pollLoop(botId, emitter),
    )
    log.infof("Bot %s registered on stream consumer group", botId)
    ()

  def unregister(botId: String): Unit =
    emitters.remove(botId)
    log.infof("Bot %s unregistered", botId)

  def registeredBots: List[String] =
    emitters.keys().asScala.toList

  private def streamKey(botId: String): String =
    s"${redisConfig.prefix}:bot:$botId:events:stream"

  private def createGroupIfAbsent(botId: String): Unit =
    Try(
      redis
        .stream(classOf[String])
        .xgroupCreate(streamKey(botId), groupName, "$", new XGroupCreateArgs().mkstream()),
    ) match
      case Failure(ex) if Option(ex.getMessage).exists(_.contains("BUSYGROUP")) => ()
      case Failure(ex) => log.warnf(ex, "Failed to create consumer group for bot %s", botId)
      case Success(_)  => ()

  private def pollLoop(botId: String, myEmitter: MultiEmitter[? >: String]): Unit =
    while emitters.get(botId) eq myEmitter do
      Try {
        val messages = redis
          .stream(classOf[String])
          .xreadgroup(
            groupName,
            consumerId,
            streamKey(botId),
            ">",
            new XReadGroupArgs().count(10).block(Duration.ofSeconds(2)),
          )
        Option(messages).foreach(_.forEach { msg =>
          if emitters.get(botId) eq myEmitter then
            myEmitter.emit(msg.payload().get("data"))
            ack(botId, msg.id())
        })
      } match
        case Failure(ex) => log.warnf(ex, "Error in poll loop for bot %s", botId)
        case Success(_)  => ()

  private def ack(botId: String, id: String): Unit =
    Try(redis.stream(classOf[String]).xack(streamKey(botId), groupName, id)) match
      case Failure(ex) => log.warnf(ex, "Failed to ack message %s for bot %s", id, botId)
      case Success(_)  => ()
