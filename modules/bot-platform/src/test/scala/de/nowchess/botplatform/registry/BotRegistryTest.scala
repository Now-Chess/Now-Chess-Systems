package de.nowchess.botplatform.registry

import de.nowchess.botplatform.config.RedisConfig
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.stream.{StreamCommands, XGroupCreateArgs}
import io.smallrye.mutiny.subscription.MultiEmitter
import org.eclipse.microprofile.context.ManagedExecutor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

class BotRegistryTest:

  // scalafix:off DisableSyntax.var
  private var registry: BotRegistry  = scala.compiletime.uninitialized
  private var redis: RedisDataSource = scala.compiletime.uninitialized
  private var streamCmds: StreamCommands[String, String, Nothing] =
    scala.compiletime.uninitialized
  private var redisConfig: RedisConfig  = scala.compiletime.uninitialized
  private var executor: ManagedExecutor = scala.compiletime.uninitialized
  // scalafix:on DisableSyntax.var

  @BeforeEach
  def setup(): Unit =
    redis = mock(classOf[RedisDataSource])
    streamCmds = mock(classOf[StreamCommands[String, String, Nothing]])
    redisConfig = mock(classOf[RedisConfig])
    executor = mock(classOf[ManagedExecutor])

    when(redis.stream(classOf[String])).thenReturn(streamCmds)
    when(redisConfig.prefix).thenReturn("nowchess")

    registry = new BotRegistry
    registry.redis = redis
    registry.redisConfig = redisConfig
    registry.executor = executor

  @Test
  def registerStartsPollThread(): Unit =
    val emitter = mock(classOf[MultiEmitter[String]])
    registry.register("bot1", emitter)
    verify(executor).submit(any(classOf[Runnable]))

  @Test
  def registerCreatesConsumerGroupWithMkstream(): Unit =
    val emitter = mock(classOf[MultiEmitter[String]])
    registry.register("bot1", emitter)
    verify(streamCmds)
      .xgroupCreate(
        org.mockito.ArgumentMatchers.eq("nowchess:bot:bot1:events:stream"),
        org.mockito.ArgumentMatchers.eq("bot-platform-consumer"),
        org.mockito.ArgumentMatchers.eq("$"),
        any(classOf[XGroupCreateArgs]),
      )

  @Test
  def registerTracksBot(): Unit =
    val emitter = mock(classOf[MultiEmitter[String]])
    registry.register("bot42", emitter)
    assertTrue(registry.registeredBots.contains("bot42"))

  @Test
  def unregisterRemovesBot(): Unit =
    val emitter = mock(classOf[MultiEmitter[String]])
    registry.register("botX", emitter)
    registry.unregister("botX")
    assertFalse(registry.registeredBots.contains("botX"))

  @Test
  def busyGroupExceptionIsIgnoredOnRegister(): Unit =
    val emitter = mock(classOf[MultiEmitter[String]])
    when(streamCmds.xgroupCreate(any(), any(), any(), any()))
      .thenThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"))
    val exec: Executable = () => registry.register("botBusy", emitter)
    assertDoesNotThrow(exec)

  @Test
  def registerDoesNotInteractWithPubSub(): Unit =
    val emitter = mock(classOf[MultiEmitter[String]])
    registry.register("botNoPubSub", emitter)
    verify(redis, never()).pubsub(any(classOf[Class[?]]))
