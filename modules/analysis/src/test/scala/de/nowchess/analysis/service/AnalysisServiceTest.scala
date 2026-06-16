package de.nowchess.analysis.service

import de.nowchess.analysis.client.{ChessApiClient, ChessApiRequestDto, ChessApiResponseDto}
import de.nowchess.analysis.dto.AnalysisRequestDto
import de.nowchess.analysis.error.{AnalysisUpstreamException, InvalidFenException}
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.{DisplayName, Test}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}

import scala.compiletime.uninitialized

// scalafix:off
@QuarkusTest
@DisplayName("AnalysisService")
class AnalysisServiceTest:

  @Inject
  var service: AnalysisService = uninitialized

  @InjectMock
  @RestClient
  var chessApiClient: ChessApiClient = uninitialized

  private val validFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  @Test
  @DisplayName("analyse returns response with best move from chess-api.com")
  def testAnalyseReturnsBestMove(): Unit =
    when(chessApiClient.analyse(any()))
      .thenReturn(
        ChessApiResponseDto(
          move = Some("e2e4"),
          centipawns = Some(0.3),
          mate = None,
          pv = Some("e2e4 e7e5 g1f3"),
          depth = Some(12),
        ),
      )

    val response = service.analyse(AnalysisRequestDto(validFen, Some(12)))

    assertEquals(validFen, response.fen)
    assertEquals(12, response.depth)
    assertEquals(Some("e2e4"), response.bestMove)
    assertEquals(Some(0.3), response.evaluation)
    assertEquals(None, response.mate)
    assertEquals(List("e2e4", "e7e5", "g1f3"), response.continuationMoves)

  @Test
  @DisplayName("analyse uses default depth 12 when not specified")
  def testAnalyseUsesDefaultDepth(): Unit =
    when(chessApiClient.analyse(any()))
      .thenReturn(ChessApiResponseDto(move = Some("d2d4"), depth = Some(12)))

    val response = service.analyse(AnalysisRequestDto(validFen))

    verify(chessApiClient).analyse(ChessApiRequestDto(validFen, 12))
    assertEquals(12, response.depth)

  @Test
  @DisplayName("analyse clamps depth to [1, 99]")
  def testAnalyseClampsDepth(): Unit =
    when(chessApiClient.analyse(any()))
      .thenReturn(ChessApiResponseDto(move = Some("e2e4"), depth = Some(99)))

    service.analyse(AnalysisRequestDto(validFen, Some(200)))

    verify(chessApiClient).analyse(ChessApiRequestDto(validFen, 99))

  @Test
  @DisplayName("analyse clamps depth minimum to 1")
  def testAnalyseClampsDepthMin(): Unit =
    when(chessApiClient.analyse(any()))
      .thenReturn(ChessApiResponseDto(move = Some("e2e4"), depth = Some(1)))

    service.analyse(AnalysisRequestDto(validFen, Some(0)))

    verify(chessApiClient).analyse(ChessApiRequestDto(validFen, 1))

  @Test
  @DisplayName("analyse handles empty pv gracefully")
  def testAnalyseEmptyPv(): Unit =
    when(chessApiClient.analyse(any()))
      .thenReturn(ChessApiResponseDto(move = Some("e2e4"), pv = None, depth = Some(5)))

    val response = service.analyse(AnalysisRequestDto(validFen, Some(5)))

    assertEquals(List.empty, response.continuationMoves)

  @Test
  @DisplayName("analyse throws InvalidFenException for empty FEN")
  def testAnalyseThrowsOnEmptyFen(): Unit =
    assertThrows(
      classOf[InvalidFenException],
      () => service.analyse(AnalysisRequestDto("")),
    )

  @Test
  @DisplayName("analyse throws InvalidFenException for malformed FEN")
  def testAnalyseThrowsOnMalformedFen(): Unit =
    assertThrows(
      classOf[InvalidFenException],
      () => service.analyse(AnalysisRequestDto("not/a/valid/fen")),
    )

  @Test
  @DisplayName("analyse wraps chess-api.com exception in AnalysisUpstreamException")
  def testAnalyseWrapsUpstreamException(): Unit =
    when(chessApiClient.analyse(any()))
      .thenThrow(new RuntimeException("connection refused"))

    assertThrows(
      classOf[AnalysisUpstreamException],
      () => service.analyse(AnalysisRequestDto(validFen)),
    )

  @Test
  @DisplayName("analyse returns mate value from chess-api.com response")
  def testAnalyseReturnsMate(): Unit =
    when(chessApiClient.analyse(any()))
      .thenReturn(
        ChessApiResponseDto(
          move = Some("d1h5"),
          centipawns = None,
          mate = Some(3),
          depth = Some(10),
        ),
      )

    val response = service.analyse(AnalysisRequestDto(validFen, Some(10)))

    assertEquals(Some(3), response.mate)
    assertEquals(None, response.evaluation)
// scalafix:on
