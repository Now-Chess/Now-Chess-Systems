package de.nowchess.account.service

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.account.config.RedisConfig
import de.nowchess.api.event.{EventEnvelope, EventType}
import io.quarkus.redis.datasource.RedisDataSource
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import scala.compiletime.uninitialized

@ApplicationScoped
class EventPublisher:

  // scalafix:off DisableSyntax.var
  @Inject var redis: RedisDataSource     = uninitialized
  @Inject var redisConfig: RedisConfig   = uninitialized
  @Inject var objectMapper: ObjectMapper = uninitialized
  // scalafix:on DisableSyntax.var

  def publishGameStart(botId: String, gameId: String, playingAs: String, difficulty: Int, botAccountId: String): Unit =
    val payload = objectMapper.createObjectNode()
    payload.put("gameId", gameId)
    payload.put("playingAs", playingAs)
    payload.put("difficulty", difficulty)
    payload.put("botAccountId", botAccountId)
    publish(s"${redisConfig.prefix}:bot:$botId:events", EventType.GameStart, payload)

  def publishChallengeCreated(destUserId: String, challengeId: String, challengerName: String): Unit =
    val payload = objectMapper.createObjectNode()
    payload.put("challengeId", challengeId)
    payload.put("challengerName", challengerName)
    publish(s"${redisConfig.prefix}:user:$destUserId:events", EventType.ChallengeCreated, payload)

  def publishChallengeAccepted(challengerId: String, challengeId: String, gameId: String): Unit =
    val payload = objectMapper.createObjectNode()
    payload.put("challengeId", challengeId)
    payload.put("gameId", gameId)
    publish(s"${redisConfig.prefix}:user:$challengerId:events", EventType.ChallengeAccepted, payload)

  private def publish(
      channel: String,
      eventType: EventType,
      payload: com.fasterxml.jackson.databind.node.ObjectNode,
  ): Unit =
    val envelope = EventEnvelope.of(eventType, payload)
    redis.pubsub(classOf[String]).publish(channel, objectMapper.writeValueAsString(envelope))
    ()
