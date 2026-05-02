package de.nowchess.botplatform.registry

import de.nowchess.botplatform.config.RedisConfig
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.pubsub.PubSubCommands
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

@ApplicationScoped
class BotRegistry:

  private val log = Logger.getLogger(classOf[BotRegistry])

  // scalafix:off DisableSyntax.var
  @Inject var redis: RedisDataSource   = uninitialized
  @Inject var redisConfig: RedisConfig = uninitialized
  // scalafix:on DisableSyntax.var

  private val connections = ConcurrentHashMap[String, (MultiEmitter[? >: String], PubSubCommands.RedisSubscriber)]()

  def register(botId: String, emitter: MultiEmitter[? >: String]): Unit =
    val channel                   = s"${redisConfig.prefix}:bot:$botId:events"
    val handler: Consumer[String] = msg => emitter.emit(msg)
    val subscriber                = redis.pubsub(classOf[String]).subscribe(channel, handler)
    connections.put(botId, (emitter, subscriber))
    log.infof("Bot %s registered", botId)
    ()

  def unregister(botId: String): Unit =
    Option(connections.remove(botId)).foreach { (_, subscriber) =>
      subscriber.unsubscribe(s"${redisConfig.prefix}:bot:$botId:events")
    }
    log.infof("Bot %s unregistered", botId)

  def dispatch(botId: String, event: String): Unit =
    log.debugf("Dispatching event to bot %s", botId)
    redis.pubsub(classOf[String]).publish(s"${redisConfig.prefix}:bot:$botId:events", event)
    ()

  def registeredBots: List[String] =
    import scala.jdk.CollectionConverters.*
    connections.keys().asScala.toList
