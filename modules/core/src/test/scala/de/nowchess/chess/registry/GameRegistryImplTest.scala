package de.nowchess.chess.registry

import de.nowchess.api.game.GameContext
import de.nowchess.api.player.{PlayerId, PlayerInfo}
import de.nowchess.chess.client.{CombinedExportResponse, StoreServiceClient}
import de.nowchess.chess.grpc.IoGrpcClientWrapper
import de.nowchess.io.fen.FenExporter
import de.nowchess.io.pgn.PgnExporter
import de.nowchess.rules.sets.DefaultRules
import de.nowchess.chess.engine.GameEngine
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.junit.jupiter.api.{BeforeEach, DisplayName, Test}
import org.junit.jupiter.api.Assertions.*
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock

import scala.compiletime.uninitialized

// scalafix:off
@QuarkusTest
@DisplayName("GameRegistryImpl")
class GameRegistryImplTest:

  @Inject
  var registry: GameRegistry = uninitialized

  @InjectMock
  var ioWrapper: IoGrpcClientWrapper = uninitialized

  @InjectMock
  @RestClient
  var storeClient: StoreServiceClient = uninitialized

  @BeforeEach
  def setupMocks(): Unit =
    when(ioWrapper.exportCombined(any())).thenAnswer((inv: InvocationOnMock) =>
      val ctx = inv.getArgument[GameContext](0)
      CombinedExportResponse(FenExporter.exportGameContext(ctx), PgnExporter.exportGameContext(ctx)),
    )
    when(ioWrapper.importPgn(any[String]())).thenAnswer((inv: InvocationOnMock) =>
      de.nowchess.io.pgn.PgnParser
        .importGameContext(inv.getArgument[String](0))
        .getOrElse(GameContext.initial),
    )

  @Test
  @DisplayName("store saves entry")
  def testStore(): Unit =
    val entry = GameEntry(
      "g1",
      GameEngine(ruleSet = DefaultRules),
      PlayerInfo(PlayerId("p1"), "P1"),
      PlayerInfo(PlayerId("p2"), "P2"),
    )
    registry.store(entry)
    assertTrue(registry.get("g1").isDefined)

  @Test
  @DisplayName("get returns stored entry")
  def testGet(): Unit =
    val entry = GameEntry(
      "g2",
      GameEngine(ruleSet = DefaultRules),
      PlayerInfo(PlayerId("p1"), "P1"),
      PlayerInfo(PlayerId("p2"), "P2"),
    )
    registry.store(entry)
    val retrieved = registry.get("g2")
    assertTrue(retrieved.isDefined)
    assertEquals("g2", retrieved.get.gameId)

  @Test
  @DisplayName("get returns None for unknown id")
  def testGetUnknown(): Unit =
    assertTrue(registry.get("unknown").isEmpty)

  @Test
  @DisplayName("update modifies existing entry")
  def testUpdate(): Unit =
    val entry = GameEntry(
      "g3",
      GameEngine(ruleSet = DefaultRules),
      PlayerInfo(PlayerId("p1"), "P1"),
      PlayerInfo(PlayerId("p2"), "P2"),
    )
    registry.store(entry)
    val updated = entry.copy(resigned = true)
    registry.update(updated)
    val retrieved = registry.get("g3")
    assertTrue(retrieved.isDefined)
    assertTrue(retrieved.get.resigned)

  @Test
  @DisplayName("generateId produces unique ids")
  def testGenerateId(): Unit =
    val id1 = registry.generateId()
    val id2 = registry.generateId()
    assertNotEquals(id1, id2)
    assertFalse(id1.isEmpty)
    assertFalse(id2.isEmpty)
// scalafix:on
