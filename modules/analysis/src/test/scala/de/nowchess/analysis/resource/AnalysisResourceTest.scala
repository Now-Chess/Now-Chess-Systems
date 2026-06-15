package de.nowchess.analysis.resource

import de.nowchess.analysis.dto.{AnalysisRequestDto, AnalysisResponseDto}
import de.nowchess.analysis.error.{AnalysisUpstreamException, InvalidFenException}
import de.nowchess.analysis.service.AnalysisService
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.{DisplayName, Test}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when

import scala.compiletime.uninitialized

// scalafix:off
@QuarkusTest
@DisplayName("AnalysisResource")
class AnalysisResourceTest:

  @InjectMock
  var analysisService: AnalysisService = uninitialized

  private val validFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  private def givenJson() = RestAssured.`given`().contentType(ContentType.JSON)

  @Test
  @DisplayName("POST /api/analysis/position returns 200 with analysis data")
  def testAnalysePositionOk(): Unit =
    when(analysisService.analyse(any()))
      .thenReturn(
        AnalysisResponseDto(
          fen = validFen,
          depth = 12,
          bestMove = Some("e2e4"),
          evaluation = Some(0.3),
          mate = None,
          continuationMoves = List("e2e4", "e7e5"),
        ),
      )

    givenJson()
      .body(s"""{"fen": "$validFen"}""")
      .when()
      .post("/api/analysis/position")
      .`then`()
      .statusCode(200)
      .body("fen", equalTo(validFen))
      .body("depth", equalTo(12))
      .body("bestMove", equalTo("e2e4"))
      .body("evaluation", equalTo(0.3f))
      .body("continuationMoves", hasItems("e2e4", "e7e5"))

  @Test
  @DisplayName("POST /api/analysis/position returns 400 for invalid FEN")
  def testAnalysePositionInvalidFen(): Unit =
    when(analysisService.analyse(any()))
      .thenThrow(new InvalidFenException("bad-fen"))

    givenJson()
      .body("""{"fen": "bad-fen"}""")
      .when()
      .post("/api/analysis/position")
      .`then`()
      .statusCode(400)
      .body("code", equalTo("INVALID_FEN"))

  @Test
  @DisplayName("POST /api/analysis/position returns 502 on upstream failure")
  def testAnalysePositionUpstreamError(): Unit =
    when(analysisService.analyse(any()))
      .thenThrow(new AnalysisUpstreamException(new RuntimeException("timeout")))

    givenJson()
      .body(s"""{"fen": "$validFen"}""")
      .when()
      .post("/api/analysis/position")
      .`then`()
      .statusCode(502)
      .body("code", equalTo("UPSTREAM_ERROR"))

  @Test
  @DisplayName("POST /api/analysis/position accepts custom depth")
  def testAnalysePositionCustomDepth(): Unit =
    when(analysisService.analyse(any()))
      .thenReturn(
        AnalysisResponseDto(
          fen = validFen,
          depth = 20,
          bestMove = Some("d2d4"),
          evaluation = Some(0.15),
          mate = None,
          continuationMoves = List.empty,
        ),
      )

    givenJson()
      .body(s"""{"fen": "$validFen", "depth": 20}""")
      .when()
      .post("/api/analysis/position")
      .`then`()
      .statusCode(200)
      .body("depth", equalTo(20))
// scalafix:on
