package de.nowchess.account.service

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.account.config.RedisConfig
import de.nowchess.api.event.{EventEnvelope, EventType}
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.stream.XAddArgs
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@ApplicationScoped
class EventPublisher:

  // scalafix:off DisableSyntax.var
  @Inject var redis: RedisDataSource     = uninitialized
  @Inject var redisConfig: RedisConfig   = uninitialized
  @Inject var objectMapper: ObjectMapper = uninitialized
  // scalafix:on DisableSyntax.var

  private val maxStreamLen = 1000L

  def publishGameStart(botId: String, gameId: String, playingAs: String, difficulty: Int, botAccountId: String): Unit =
    val payload = objectMapper.createObjectNode()
    payload.put("gameId", gameId)
    payload.put("playingAs", playingAs)
    payload.put("difficulty", difficulty)
    payload.put("botAccountId", botAccountId)
    val envelope = EventEnvelope.of(EventType.BotGameStart, payload)
    val json     = objectMapper.writeValueAsString(envelope)
    redis
      .stream(classOf[String])
      .xadd(
        s"${redisConfig.prefix}:bot:$botId:events:stream",
        new XAddArgs().maxlen(maxStreamLen).nearlyExactTrimming(),
        Map("data" -> json).asJava,
      )
    ()

  def publishChallengeCreated(destUserId: String, challengeId: String, challengerName: String): Unit =
    val payload = objectMapper.createObjectNode()
    payload.put("challengeId", challengeId)
    payload.put("challengerName", challengerName)
    publishToUserStream(destUserId, EventType.ChallengeCreated, payload)

  def publishChallengeAccepted(challengerId: String, challengeId: String, gameId: String): Unit =
    val payload = objectMapper.createObjectNode()
    payload.put("challengeId", challengeId)
    payload.put("gameId", gameId)
    publishToUserStream(challengerId, EventType.ChallengeAccepted, payload)

  private def publishToUserStream(
      userId: String,
      eventType: EventType,
      payload: com.fasterxml.jackson.databind.node.ObjectNode,
  ): Unit =
    val envelope = EventEnvelope.of(eventType, payload)
    val json     = objectMapper.writeValueAsString(envelope)
    redis
      .stream(classOf[String])
      .xadd(
        s"${redisConfig.prefix}:user:$userId:events:stream",
        new XAddArgs().maxlen(maxStreamLen).nearlyExactTrimming(),
        Map("data" -> json).asJava,
      )
    ()
