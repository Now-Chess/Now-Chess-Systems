package de.nowchess.chess.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import de.nowchess.api.board.Color
import de.nowchess.api.game.{DrawReason, GameContext, GameResult, WinReason}
import de.nowchess.api.player.{PlayerId, PlayerInfo}
import de.nowchess.chess.client.CombinedExportResponse
import de.nowchess.chess.engine.GameEngine
import de.nowchess.chess.grpc.IoGrpcClientWrapper
import de.nowchess.chess.observer.GameEvent
import de.nowchess.chess.registry.{GameEntry, GameRegistry}
import de.nowchess.rules.sets.DefaultRules
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.pubsub.PubSubCommands
import io.quarkus.redis.datasource.stream.{StreamCommands, XAddArgs}
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import scala.compiletime.uninitialized

class GameRedisPublisherTest:

  // scalafix:off DisableSyntax.var
  private var redis: RedisDataSource                              = uninitialized
  private var streamCmds: StreamCommands[String, String, Nothing] = uninitialized
  private var pubsubCmds: PubSubCommands[String]                  = uninitialized
  private var registry: GameRegistry                              = uninitialized
  private var ioClient: IoGrpcClientWrapper                       = uninitialized
  private var onGameOverCalled: Boolean                           = false
  // scalafix:on DisableSyntax.var

  private val objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
  private val gameId       = "game1"
  private val whitePlayer  = PlayerInfo(PlayerId("white1"), "Alice")
  private val blackPlayer  = PlayerInfo(PlayerId("black1"), "Bob")

  @BeforeEach
  def setup(): Unit =
    redis = mock(classOf[RedisDataSource])
    streamCmds = mock(classOf[StreamCommands[String, String, Nothing]])
    pubsubCmds = mock(classOf[PubSubCommands[String]])
    registry = mock(classOf[GameRegistry])
    ioClient = mock(classOf[IoGrpcClientWrapper])
    when(redis.stream(classOf[String])).thenReturn(streamCmds)
    when(redis.pubsub(classOf[String])).thenReturn(pubsubCmds)
    when(ioClient.exportCombined(any()))
      .thenReturn(CombinedExportResponse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", ""))
    onGameOverCalled = false

  private def publisherWithResult(result: GameResult): GameRedisPublisher =
    val ctx    = GameContext.initial.copy(result = Some(result))
    val engine = new GameEngine(initialContext = ctx, ruleSet = DefaultRules)
    val entry  = GameEntry(gameId, engine, whitePlayer, blackPlayer)
    when(registry.get(gameId)).thenReturn(Some(entry))
    new GameRedisPublisher(
      gameId,
      registry,
      redis,
      objectMapper,
      s"nowchess:game:$gameId:s2c",
      _ => (),
      ioClient,
      _ => onGameOverCalled = true,
      "nowchess",
    )

  @Test
  def publishesGameOverOnCheckmate(): Unit =
    val publisher = publisherWithResult(GameResult.Win(Color.White, WinReason.Checkmate))
    publisher.onGameEvent(mock(classOf[GameEvent]))
    verify(streamCmds).xadd(
      org.mockito.ArgumentMatchers.eq("nowchess:game-over"),
      any(classOf[XAddArgs]),
      any(),
    )
    assertTrue(onGameOverCalled)

  @Test
  def publishesGameOverOnResignation(): Unit =
    val publisher = publisherWithResult(GameResult.Win(Color.Black, WinReason.Resignation))
    publisher.onGameEvent(mock(classOf[GameEvent]))
    verify(streamCmds).xadd(
      org.mockito.ArgumentMatchers.eq("nowchess:game-over"),
      any(classOf[XAddArgs]),
      any(),
    )

  @Test
  def publishesGameOverOnDraw(): Unit =
    val publisher = publisherWithResult(GameResult.Draw(DrawReason.Agreement))
    publisher.onGameEvent(mock(classOf[GameEvent]))
    verify(streamCmds).xadd(
      org.mockito.ArgumentMatchers.eq("nowchess:game-over"),
      any(classOf[XAddArgs]),
      any(),
    )

  @Test
  def doesNotPublishGameOverWhenNoResult(): Unit =
    val ctx    = GameContext.initial
    val engine = new GameEngine(initialContext = ctx, ruleSet = DefaultRules)
    val entry  = GameEntry(gameId, engine, whitePlayer, blackPlayer)
    when(registry.get(gameId)).thenReturn(Some(entry))
    val publisher = new GameRedisPublisher(
      gameId,
      registry,
      redis,
      objectMapper,
      s"nowchess:game:$gameId:s2c",
      _ => (),
      ioClient,
      _ => onGameOverCalled = true,
      "nowchess",
    )
    publisher.onGameEvent(mock(classOf[GameEvent]))
    verify(streamCmds, never()).xadd(
      org.mockito.ArgumentMatchers.eq("nowchess:game-over"),
      any(classOf[XAddArgs]),
      any(),
    )
    assertFalse(onGameOverCalled)
