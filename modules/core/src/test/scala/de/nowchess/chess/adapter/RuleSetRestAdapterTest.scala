package de.nowchess.chess.adapter

import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType}
import de.nowchess.chess.client.{RuleMoveRequest, RuleServiceClient, RuleSquareRequest}
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.junit.jupiter.api.{BeforeEach, DisplayName, Test}
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.{verify, when}

import scala.compiletime.uninitialized

// scalafix:off
@QuarkusTest
@DisplayName("RuleSetRestAdapter")
class RuleSetRestAdapterTest:

  @Inject
  var adapter: RuleSetRestAdapter = uninitialized

  @InjectMock
  @RestClient
  var client: RuleServiceClient = uninitialized

  private val ctx  = GameContext.initial
  private val sq   = Square(File.E, Rank.R2)
  private val move = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal(false))

  @BeforeEach
  def setup(): Unit =
    when(client.candidateMoves(RuleSquareRequest(ctx, sq.toString))).thenReturn(List(move))
    when(client.legalMoves(RuleSquareRequest(ctx, sq.toString))).thenReturn(List(move))
    when(client.allLegalMoves(ctx)).thenReturn(List(move))
    when(client.isCheck(ctx)).thenReturn(false)
    when(client.isCheckmate(ctx)).thenReturn(false)
    when(client.isStalemate(ctx)).thenReturn(false)
    when(client.isInsufficientMaterial(ctx)).thenReturn(false)
    when(client.isFiftyMoveRule(ctx)).thenReturn(false)
    when(client.isThreefoldRepetition(ctx)).thenReturn(false)
    when(client.applyMove(RuleMoveRequest(ctx, move))).thenReturn(ctx)

  @Test
  @DisplayName("candidateMoves delegates to client")
  def testCandidateMoves(): Unit =
    val result = adapter.candidateMoves(ctx)(sq)
    assertEquals(List(move), result)
    verify(client).candidateMoves(RuleSquareRequest(ctx, sq.toString))

  @Test
  @DisplayName("legalMoves delegates to client")
  def testLegalMoves(): Unit =
    val result = adapter.legalMoves(ctx)(sq)
    assertEquals(List(move), result)
    verify(client).legalMoves(RuleSquareRequest(ctx, sq.toString))

  @Test
  @DisplayName("allLegalMoves delegates to client")
  def testAllLegalMoves(): Unit =
    val result = adapter.allLegalMoves(ctx)
    assertEquals(List(move), result)
    verify(client).allLegalMoves(ctx)

  @Test
  @DisplayName("isCheck delegates to client")
  def testIsCheck(): Unit =
    assertFalse(adapter.isCheck(ctx))
    verify(client).isCheck(ctx)

  @Test
  @DisplayName("isCheckmate delegates to client")
  def testIsCheckmate(): Unit =
    assertFalse(adapter.isCheckmate(ctx))
    verify(client).isCheckmate(ctx)

  @Test
  @DisplayName("isStalemate delegates to client")
  def testIsStalemate(): Unit =
    assertFalse(adapter.isStalemate(ctx))
    verify(client).isStalemate(ctx)

  @Test
  @DisplayName("isInsufficientMaterial delegates to client")
  def testIsInsufficientMaterial(): Unit =
    assertFalse(adapter.isInsufficientMaterial(ctx))
    verify(client).isInsufficientMaterial(ctx)

  @Test
  @DisplayName("isFiftyMoveRule delegates to client")
  def testIsFiftyMoveRule(): Unit =
    assertFalse(adapter.isFiftyMoveRule(ctx))
    verify(client).isFiftyMoveRule(ctx)

  @Test
  @DisplayName("isThreefoldRepetition delegates to client")
  def testIsThreefoldRepetition(): Unit =
    assertFalse(adapter.isThreefoldRepetition(ctx))
    verify(client).isThreefoldRepetition(ctx)

  @Test
  @DisplayName("applyMove delegates to client")
  def testApplyMove(): Unit =
    val result = adapter.applyMove(ctx)(move)
    assertEquals(ctx, result)
    verify(client).applyMove(RuleMoveRequest(ctx, move))
// scalafix:on
