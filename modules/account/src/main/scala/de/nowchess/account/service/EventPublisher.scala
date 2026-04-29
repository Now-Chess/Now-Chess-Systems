package de.nowchess.account.service

import de.nowchess.account.config.RedisConfig
import io.quarkus.redis.datasource.RedisDataSource
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import scala.compiletime.uninitialized

@ApplicationScoped
class EventPublisher:

  // scalafix:off DisableSyntax.var
  @Inject var redis: RedisDataSource   = uninitialized
  @Inject var redisConfig: RedisConfig = uninitialized
  // scalafix:on DisableSyntax.var

  def publishGameStart(botId: String, gameId: String, playingAs: String, difficulty: Int, botAccountId: String): Unit =
    val event =
      s"""{"type":"gameStart","gameId":"$gameId","playingAs":"$playingAs","difficulty":$difficulty,"botAccountId":"$botAccountId"}"""
    redis.pubsub(classOf[String]).publish(s"${redisConfig.prefix}:bot:$botId:events", event)
    ()

  def publishChallengeCreated(destUserId: String, challengeId: String, challengerName: String): Unit =
    val event = s"""{"type":"challengeCreated","challengeId":"$challengeId","challengerName":"$challengerName"}"""
    redis.pubsub(classOf[String]).publish(s"${redisConfig.prefix}:user:$destUserId:events", event)
    ()

  def publishChallengeAccepted(challengerId: String, challengeId: String, gameId: String): Unit =
    val event = s"""{"type":"challengeAccepted","challengeId":"$challengeId","gameId":"$gameId"}"""
    redis.pubsub(classOf[String]).publish(s"${redisConfig.prefix}:user:$challengerId:events", event)
    ()
