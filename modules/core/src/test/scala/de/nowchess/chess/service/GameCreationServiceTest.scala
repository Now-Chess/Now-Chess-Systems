package de.nowchess.chess.service

import de.nowchess.api.dto.{GameCreationRequestDto, PlayerInfoDto, TimeControlDto}
import de.nowchess.api.game.{GameMode, TimeControl}
import de.nowchess.api.player.PlayerType
import de.nowchess.chess.client.CombinedExportResponse
import de.nowchess.chess.grpc.IoGrpcClientWrapper
import de.nowchess.chess.redis.GameRedisSubscriberManager
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.{BeforeEach, DisplayName, Test}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import scala.compiletime.uninitialized

// scalafix:off
@QuarkusTest
@DisplayName("GameCreationService")
class GameCreationServiceTest:

  @Inject
  var service: GameCreationService = uninitialized

  @InjectMock
  var subscriberManager: GameRedisSubscriberManager = uninitialized

  @InjectMock
  var ioWrapper: IoGrpcClientWrapper = uninitialized

  @BeforeEach
  def setup(): Unit =
    when(ioWrapper.exportCombined(any()))
      .thenReturn(CombinedExportResponse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", ""))

  private def player(id: String, name: String): PlayerInfoDto =
    PlayerInfoDto(id, name, PlayerType.Human)

  @Test
  def createsGameAndSubscribes(): Unit =
    val req =
      GameCreationRequestDto(Some(player("w", "White")), Some(player("b", "Black")), None, Some(GameMode.Authenticated))
    val entry = service.createGame(req)
    assertNotNull(entry.gameId)
    assertEquals("White", entry.white.displayName)
    assertEquals("Black", entry.black.displayName)
    assertEquals(GameMode.Authenticated, entry.mode)
    verify(subscriberManager).subscribeGame(entry.gameId)

  @Test
  def defaultsToOpenModeAndDefaultPlayers(): Unit =
    val entry = service.createGame(GameCreationRequestDto(None, None, None, None))
    assertEquals(GameMode.Open, entry.mode)
    assertEquals("Player 1", entry.white.displayName)
    assertEquals("Player 2", entry.black.displayName)

  @Test
  def mapsClockTimeControl(): Unit =
    val tc    = TimeControlDto(Some(300), Some(5), None)
    val entry = service.createGame(GameCreationRequestDto(None, None, Some(tc), None))
    assertEquals(TimeControl.Clock(300, 5), entry.engine.timeControl)

  @Test
  def mapsCorrespondenceTimeControl(): Unit =
    val tc    = TimeControlDto(None, None, Some(3))
    val entry = service.createGame(GameCreationRequestDto(None, None, Some(tc), None))
    assertEquals(TimeControl.Correspondence(3), entry.engine.timeControl)
