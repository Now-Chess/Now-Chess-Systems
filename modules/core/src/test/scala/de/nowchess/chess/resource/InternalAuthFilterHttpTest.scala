package de.nowchess.chess.resource

import de.nowchess.chess.grpc.{IoGrpcClientWrapper, RuleSetGrpcAdapter}
import de.nowchess.chess.redis.GameRedisSubscriberManager
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.{QuarkusTest, TestProfile}
import io.quarkus.test.junit.QuarkusTestProfile
import io.restassured.RestAssured
import jakarta.ws.rs.core.MediaType
import org.junit.jupiter.api.{DisplayName, Test}

import java.util.Map as JMap

// scalafix:off
class InternalAuthEnabledProfile extends QuarkusTestProfile:
  override def getConfigOverrides(): JMap[String, String] =
    JMap.of(
      "nowchess.internal.auth.enabled", "true",
      "nowchess.internal.secret", "test-secret-123",
    )

@QuarkusTest
@TestProfile(classOf[InternalAuthEnabledProfile])
@DisplayName("InternalAuthFilter HTTP")
class InternalAuthFilterHttpTest:

  @InjectMock
  var ioWrapper: IoGrpcClientWrapper = scala.compiletime.uninitialized

  @InjectMock
  var ruleAdapter: RuleSetGrpcAdapter = scala.compiletime.uninitialized

  @InjectMock
  var subscriberManager: GameRedisSubscriberManager = scala.compiletime.uninitialized

  @Test
  @DisplayName("POST /api/board/game without secret returns 401")
  def rejectNoSecret(): Unit =
    RestAssured.`given`()
      .contentType(MediaType.APPLICATION_JSON)
      .body("{}")
    .when()
      .post("/api/board/game")
    .`then`()
      .statusCode(401)

  @Test
  @DisplayName("POST /api/board/game with wrong secret returns 401")
  def rejectWrongSecret(): Unit =
    RestAssured.`given`()
      .contentType(MediaType.APPLICATION_JSON)
      .header("X-Internal-Secret", "wrong-secret")
      .body("{}")
    .when()
      .post("/api/board/game")
    .`then`()
      .statusCode(401)

  @Test
  @DisplayName("GET /api/board/game/{id} without secret returns 404 not 401")
  def nonInternalEndpointNotBlocked(): Unit =
    RestAssured.`given`()
    .when()
      .get("/api/board/game/nonexistent")
    .`then`()
      .statusCode(404)
// scalafix:on
