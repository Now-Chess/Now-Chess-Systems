package de.nowchess.chess.resource

import de.nowchess.api.dto.*
import de.nowchess.chess.exception.BadRequestException
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.{DisplayName, Test}
import org.junit.jupiter.api.Assertions.*

import scala.compiletime.uninitialized

// scalafix:off
@QuarkusTest
@DisplayName("GameResource Integration")
class GameResourceIntegrationTest:

  @Inject
  var resource: GameResource = uninitialized

  @Test
  @DisplayName("createGame returns 201")
  def testCreateGame(): Unit =
    val req  = CreateGameRequestDto(None, None)
    val resp = resource.createGame(req)
    assertEquals(201, resp.getStatus)
    val dto = resp.getEntity.asInstanceOf[GameFullDto]
    assertNotNull(dto.gameId)

  @Test
  @DisplayName("getGame returns 200")
  def testGetGame(): Unit =
    val createResp = resource.createGame(CreateGameRequestDto(None, None))
    val gameId     = createResp.getEntity.asInstanceOf[GameFullDto].gameId
    val getResp    = resource.getGame(gameId)
    assertEquals(200, getResp.getStatus)
    val dto = getResp.getEntity.asInstanceOf[GameFullDto]
    assertEquals(gameId, dto.gameId)

  @Test
  @DisplayName("makeMove advances game")
  def testMakeMove(): Unit =
    val createResp = resource.createGame(CreateGameRequestDto(None, None))
    val gameId     = createResp.getEntity.asInstanceOf[GameFullDto].gameId
    val moveResp   = resource.makeMove(gameId, "e2e4")
    assertEquals(200, moveResp.getStatus)
    val state = moveResp.getEntity.asInstanceOf[GameStateDto]
    assertEquals("black", state.turn)

  @Test
  @DisplayName("makeMove with invalid UCI throws")
  def testMakeMoveInvalid(): Unit =
    val createResp = resource.createGame(CreateGameRequestDto(None, None))
    val gameId     = createResp.getEntity.asInstanceOf[GameFullDto].gameId
    assertThrows(classOf[BadRequestException], () => resource.makeMove(gameId, "invalid"))

  @Test
  @DisplayName("getLegalMoves returns moves")
  def testGetLegalMoves(): Unit =
    val createResp = resource.createGame(CreateGameRequestDto(None, None))
    val gameId     = createResp.getEntity.asInstanceOf[GameFullDto].gameId
    val movesResp  = resource.getLegalMoves(gameId, "")
    assertEquals(200, movesResp.getStatus)
    val dto = movesResp.getEntity.asInstanceOf[LegalMovesResponseDto]
    assertFalse(dto.moves.isEmpty)

  @Test
  @DisplayName("resignGame updates state")
  def testResignGame(): Unit =
    val createResp = resource.createGame(CreateGameRequestDto(None, None))
    val gameId     = createResp.getEntity.asInstanceOf[GameFullDto].gameId
    val resignResp = resource.resignGame(gameId)
    assertEquals(200, resignResp.getStatus)
    val getResp = resource.getGame(gameId)
    val state   = getResp.getEntity.asInstanceOf[GameFullDto].state
    assertEquals("resign", state.status)

  @Test
  @DisplayName("undoMove reverts")
  def testUndoMove(): Unit =
    val createResp = resource.createGame(CreateGameRequestDto(None, None))
    val gameId     = createResp.getEntity.asInstanceOf[GameFullDto].gameId
    resource.makeMove(gameId, "e2e4")
    val undoResp = resource.undoMove(gameId)
    assertEquals(200, undoResp.getStatus)
    val state = undoResp.getEntity.asInstanceOf[GameStateDto]
    assertEquals("white", state.turn)

  @Test
  @DisplayName("redoMove restores")
  def testRedoMove(): Unit =
    val createResp = resource.createGame(CreateGameRequestDto(None, None))
    val gameId     = createResp.getEntity.asInstanceOf[GameFullDto].gameId
    resource.makeMove(gameId, "e2e4")
    resource.undoMove(gameId)
    val redoResp = resource.redoMove(gameId)
    assertEquals(200, redoResp.getStatus)
    val state = redoResp.getEntity.asInstanceOf[GameStateDto]
    assertEquals("black", state.turn)

  @Test
  @DisplayName("drawAction offer")
  def testDrawActionOffer(): Unit =
    val createResp = resource.createGame(CreateGameRequestDto(None, None))
    val gameId     = createResp.getEntity.asInstanceOf[GameFullDto].gameId
    val resp       = resource.drawAction(gameId, "offer")
    assertEquals(200, resp.getStatus)

  @Test
  @DisplayName("drawAction accept")
  def testDrawActionAccept(): Unit =
    val createResp = resource.createGame(CreateGameRequestDto(None, None))
    val gameId     = createResp.getEntity.asInstanceOf[GameFullDto].gameId
    resource.drawAction(gameId, "offer")
    val resp = resource.drawAction(gameId, "accept")
    assertEquals(200, resp.getStatus)

  @Test
  @DisplayName("importFen creates game")
  def testImportFen(): Unit =
    val fen  = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val req  = ImportFenRequestDto(fen, None, None)
    val resp = resource.importFen(req)
    assertEquals(201, resp.getStatus)
    val dto = resp.getEntity.asInstanceOf[GameFullDto]
    assertEquals(fen, dto.state.fen)

  @Test
  @DisplayName("importPgn creates game")
  def testImportPgn(): Unit =
    val req  = ImportPgnRequestDto("1. e4 c5")
    val resp = resource.importPgn(req)
    assertEquals(201, resp.getStatus)
    val dto = resp.getEntity.asInstanceOf[GameFullDto]
    assertTrue(dto.state.moves.length > 0)

  @Test
  @DisplayName("exportFen returns FEN")
  def testExportFen(): Unit =
    val createResp = resource.createGame(CreateGameRequestDto(None, None))
    val gameId     = createResp.getEntity.asInstanceOf[GameFullDto].gameId
    val resp       = resource.exportFen(gameId)
    assertEquals(200, resp.getStatus)
    assertTrue(resp.getEntity.asInstanceOf[String].contains("rnbqkbnr"))

  @Test
  @DisplayName("exportPgn returns PGN")
  def testExportPgn(): Unit =
    val createResp = resource.createGame(CreateGameRequestDto(None, None))
    val gameId     = createResp.getEntity.asInstanceOf[GameFullDto].gameId
    resource.makeMove(gameId, "e2e4")
    val resp = resource.exportPgn(gameId)
    assertEquals(200, resp.getStatus)
    assertTrue(resp.getEntity.asInstanceOf[String].contains("1."))
// scalafix:on
