package de.nowchess.account.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import de.nowchess.account.config.RedisConfig
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.stream.{StreamCommands, XAddArgs}
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import scala.compiletime.uninitialized

class EventPublisherTest:

  // scalafix:off DisableSyntax.var
  private var redis: RedisDataSource                              = uninitialized
  private var streamCmds: StreamCommands[String, String, Nothing] = uninitialized
  private var redisConfig: RedisConfig                            = uninitialized
  // scalafix:on DisableSyntax.var

  private val objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())

  @BeforeEach
  def setup(): Unit =
    redis = mock(classOf[RedisDataSource])
    streamCmds = mock(classOf[StreamCommands[String, String, Nothing]])
    redisConfig = mock(classOf[RedisConfig])
    when(redis.stream(classOf[String])).thenReturn(streamCmds)
    when(redisConfig.prefix).thenReturn("nowchess")

  private def publisher: EventPublisher =
    val p = new EventPublisher
    p.redis = redis
    p.redisConfig = redisConfig
    p.objectMapper = objectMapper
    p

  @Test
  def publishChallengeCreatedWritesToUserStream(): Unit =
    publisher.publishChallengeCreated("user1", "ch1", "Alice")
    verify(streamCmds).xadd(
      org.mockito.ArgumentMatchers.eq("nowchess:user:user1:events:stream"),
      any(classOf[XAddArgs]),
      any(),
    )
    verify(redis, never()).pubsub(any(classOf[Class[?]]))

  @Test
  def publishChallengeAcceptedWritesToUserStream(): Unit =
    publisher.publishChallengeAccepted("user2", "ch1", "game42")
    verify(streamCmds).xadd(
      org.mockito.ArgumentMatchers.eq("nowchess:user:user2:events:stream"),
      any(classOf[XAddArgs]),
      any(),
    )
    verify(redis, never()).pubsub(any(classOf[Class[?]]))
