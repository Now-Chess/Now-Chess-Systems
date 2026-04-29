package de.nowchess.account.resource

import de.nowchess.account.client.{CoreGameClient, CoreGameResponse}
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.{ArgumentMatchers, Mockito}

@QuarkusTest
class ChallengeResourceTest:

  @InjectMock
  @RestClient
  // scalafix:off DisableSyntax.var
  var coreGameClient: CoreGameClient = scala.compiletime.uninitialized
  // scalafix:on

  @BeforeEach
  def setup(): Unit =
    Mockito.when(coreGameClient.createGame(ArgumentMatchers.any())).thenReturn(CoreGameResponse("test-game-id"))

  private def givenRequest() = RestAssured.`given`().contentType(ContentType.JSON)

  private def registerBody(username: String, suffix: String = "") =
    val email = s"$username$suffix@test.com"
    s"""{"username":"$username$suffix","email":"$email","password":"secret"}"""

  private def loginBody(username: String, suffix: String = "") =
    s"""{"username":"$username$suffix","password":"secret"}"""

  private def registerAndLogin(username: String, suffix: String = ""): String =
    givenRequest().body(registerBody(username, suffix)).when().post("/api/account")
    givenRequest()
      .body(loginBody(username, suffix))
      .when()
      .post("/api/account/login")
      .`then`()
      .statusCode(200)
      .extract()
      .path[String]("token")

  private val clockBody =
    """{"color":"random","timeControl":{"type":"clock","limit":300,"increment":5}}"""

  private def authed(token: String) =
    givenRequest().header("Authorization", s"Bearer $token")

  @Test
  def createChallengeReturns201(): Unit =
    val t1 = registerAndLogin("user1c")
    registerAndLogin("user2c")
    authed(t1)
      .contentType(ContentType.JSON)
      .body(clockBody)
      .when()
      .post("/api/challenge/user2c")
      .`then`()
      .statusCode(201)
      .body("status", is("created"))
      .body("color", is("random"))

  @Test
  def createChallengeConflictOnDuplicate(): Unit =
    val t1 = registerAndLogin("user1dup")
    registerAndLogin("user2dup")
    authed(t1).contentType(ContentType.JSON).body(clockBody).when().post("/api/challenge/user2dup")
    authed(t1)
      .contentType(ContentType.JSON)
      .body(clockBody)
      .when()
      .post("/api/challenge/user2dup")
      .`then`()
      .statusCode(409)

  @Test
  def createChallengeSelfForbidden(): Unit =
    val token = registerAndLogin("selfuser")
    authed(token)
      .contentType(ContentType.JSON)
      .body(clockBody)
      .when()
      .post("/api/challenge/selfuser")
      .`then`()
      .statusCode(400)

  @Test
  def acceptChallengeReturns200(): Unit =
    val t1 = registerAndLogin("accUser1")
    val t2 = registerAndLogin("accUser2")
    val challengeId = authed(t1)
      .contentType(ContentType.JSON)
      .body(clockBody)
      .when()
      .post("/api/challenge/accUser2")
      .`then`()
      .statusCode(201)
      .extract()
      .path[String]("id")
    authed(t2)
      .when()
      .post(s"/api/challenge/$challengeId/accept")
      .`then`()
      .statusCode(200)
      .body("status", is("accepted"))
      .body("gameId", is("test-game-id"))

  @Test
  def declineChallengeReturns200(): Unit =
    val t1 = registerAndLogin("decUser1")
    val t2 = registerAndLogin("decUser2")
    val challengeId = authed(t1)
      .contentType(ContentType.JSON)
      .body(clockBody)
      .when()
      .post("/api/challenge/decUser2")
      .`then`()
      .statusCode(201)
      .extract()
      .path[String]("id")
    authed(t2)
      .contentType(ContentType.JSON)
      .body("""{"reason":"later"}""")
      .when()
      .post(s"/api/challenge/$challengeId/decline")
      .`then`()
      .statusCode(200)
      .body("status", is("declined"))
      .body("declineReason", is("later"))

  @Test
  def cancelChallengeReturns200(): Unit =
    val t1 = registerAndLogin("canUser1")
    registerAndLogin("canUser2")
    val challengeId = authed(t1)
      .contentType(ContentType.JSON)
      .body(clockBody)
      .when()
      .post("/api/challenge/canUser2")
      .`then`()
      .statusCode(201)
      .extract()
      .path[String]("id")
    authed(t1)
      .when()
      .post(s"/api/challenge/$challengeId/cancel")
      .`then`()
      .statusCode(200)
      .body("status", is("canceled"))

  @Test
  def listChallengesReturnsInAndOut(): Unit =
    val t1 = registerAndLogin("listUser1")
    registerAndLogin("listUser2")
    registerAndLogin("listUser3")
    authed(t1)
      .contentType(ContentType.JSON)
      .body(clockBody)
      .when()
      .post("/api/challenge/listUser2")
      .`then`()
      .statusCode(201)
    authed(t1)
      .contentType(ContentType.JSON)
      .body(clockBody)
      .when()
      .post("/api/challenge/listUser3")
      .`then`()
      .statusCode(201)
    authed(t1)
      .when()
      .get("/api/challenge")
      .`then`()
      .statusCode(200)
      .body("out.size()", is(2))
      .body("in.size()", is(0))
