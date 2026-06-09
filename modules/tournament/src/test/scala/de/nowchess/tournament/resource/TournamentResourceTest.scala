package de.nowchess.tournament.resource

import de.nowchess.tournament.client.{CoreGameClient, CoreGameResponse}
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.response.ValidatableResponse
import io.smallrye.jwt.build.Jwt
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.{ArgumentMatchers, Mockito}

@QuarkusTest
class TournamentResourceTest:

  @InjectMock
  @RestClient
  // scalafix:off DisableSyntax.var
  var coreGameClient: CoreGameClient = scala.compiletime.uninitialized
  // scalafix:on

  @BeforeEach
  def setup(): Unit =
    Mockito.when(coreGameClient.createGame(ArgumentMatchers.any())).thenReturn(CoreGameResponse("game-test-123"))

  private def g() = RestAssured.`given`().contentType(ContentType.JSON)

  private def directorToken(userId: String = "director-1"): String =
    Jwt.issuer("nowchess").subject(userId).expiresIn(3600).sign()

  private def botToken(botId: String, botName: String): String =
    Jwt.issuer("nowchess").subject(botId).claim("type", "bot").claim("name", botName).expiresIn(3600).sign()

  private def authed(token: String) =
    g().header("Authorization", s"Bearer $token")

  private def formAuthed(token: String) =
    RestAssured.`given`().contentType(ContentType.URLENC).header("Authorization", s"Bearer $token")

  private def createTournament(token: String, name: String = "Test Tour", nbRounds: Int = 3): String =
    formAuthed(token)
      .formParam("name", name)
      .formParam("nbRounds", nbRounds)
      .formParam("clockLimit", 300)
      .formParam("clockIncrement", 5)
      .formParam("rated", true)
      .when()
      .post("/api/tournament")
      .`then`()
      .statusCode(201)
      .extract()
      .path[String]("id")

  private def postAndCheck(token: String, path: String, expectedStatus: Int): ValidatableResponse =
    authed(token).when().post(path).`then`().statusCode(expectedStatus)

  private def deleteAndCheck(token: String, path: String, expectedStatus: Int): ValidatableResponse =
    authed(token).when().delete(path).`then`().statusCode(expectedStatus)

  private def botJoin(tournamentId: String, botId: String, botName: String): ValidatableResponse =
    val bt = botToken(botId, botName)
    authed(bt).when().post(s"/api/tournament/$tournamentId/join").`then`().statusCode(200)

  private def startTournament(token: String, tournamentId: String): ValidatableResponse =
    authed(token).when().post(s"/api/tournament/$tournamentId/start").`then`().statusCode(200)

  @Test
  def createsTournamentWhenAuthenticated(): Unit =
    formAuthed(directorToken())
      .formParam("name", "Test Tour")
      .formParam("nbRounds", 3)
      .formParam("clockLimit", 300)
      .formParam("clockIncrement", 5)
      .formParam("rated", true)
      .when()
      .post("/api/tournament")
      .`then`()
      .statusCode(201)
      .body("fullName", is("Test Tour"))
      .body("status", is("created"))

  @Test
  def returns401WhenUnauthenticated(): Unit =
    RestAssured
      .`given`()
      .contentType(ContentType.URLENC)
      .formParam("name", "Test Tour")
      .formParam("nbRounds", 3)
      .formParam("clockLimit", 300)
      .formParam("clockIncrement", 5)
      .when()
      .post("/api/tournament")
      .`then`()
      .statusCode(401)

  @Test
  def returnsEmptyListsOnFreshStart(): Unit =
    RestAssured
      .`given`()
      .when()
      .get("/api/tournament")
      .`then`()
      .statusCode(200)
      .body("created", notNullValue())
      .body("started", notNullValue())
      .body("finished", notNullValue())

  @Test
  def returnsCreatedTournamentInCreatedList(): Unit =
    val id = createTournament(directorToken("director-list"), "ListTour")
    RestAssured
      .`given`()
      .when()
      .get("/api/tournament")
      .`then`()
      .statusCode(200)
      .body("created.id", hasItem(id))

  @Test
  def returns404ForUnknownId(): Unit =
    RestAssured.`given`().when().get("/api/tournament/XXXXXX").`then`().statusCode(404)

  @Test
  def returnsTournamentWithStandings(): Unit =
    val id = createTournament(directorToken("dir-get"), "GetTour")
    RestAssured
      .`given`()
      .when()
      .get(s"/api/tournament/$id")
      .`then`()
      .statusCode(200)
      .body("id", is(id))
      .body("standing", notNullValue())

  @Test
  def directorCanTerminateCreatedTournament(): Unit =
    val token = directorToken("dir-term")
    val id    = createTournament(token, "TermTour")
    deleteAndCheck(token, s"/api/tournament/$id", 204)

  @Test
  def nonDirectorGets403OnTerminate(): Unit =
    val id = createTournament(directorToken("dir-403"), "SecureTour")
    deleteAndCheck(directorToken("other-user-403"), s"/api/tournament/$id", 403)

  @Test
  def cannotTerminateStartedTournament(): Unit =
    val token = directorToken("dir-started")
    val id    = createTournament(token, "StartedTour")
    botJoin(id, "sbot-1", "StartBot1")
    botJoin(id, "sbot-2", "StartBot2")
    startTournament(token, id)
    deleteAndCheck(token, s"/api/tournament/$id", 409)

  @Test
  def botJoinsSuccessfully(): Unit =
    val id = createTournament(directorToken("dir-join"), "JoinTour")
    authed(botToken("joinbot-1", "JoinBot1"))
      .when()
      .post(s"/api/tournament/$id/join")
      .`then`()
      .statusCode(200)
      .body("ok", is(true))

  @Test
  def nonBotTokenReturns403OnJoin(): Unit =
    val id = createTournament(directorToken("dir-nbjoin"), "NbJoinTour")
    postAndCheck(directorToken("regular-user"), s"/api/tournament/$id/join", 403)

  @Test
  def alreadyJoinedReturns409(): Unit =
    val id = createTournament(directorToken("dir-dbl"), "DblJoinTour")
    val bt = botToken("dblbot-1", "DblBot1")
    botJoin(id, "dblbot-1", "DblBot1")
    authed(bt).when().post(s"/api/tournament/$id/join").`then`().statusCode(409)

  @Test
  def startedTournamentReturns409OnJoin(): Unit =
    val token = directorToken("dir-sjoin")
    val id    = createTournament(token, "SjoinTour")
    botJoin(id, "sjbot-1", "SjBot1")
    botJoin(id, "sjbot-2", "SjBot2")
    startTournament(token, id)
    authed(botToken("sjbot-3", "SjBot3")).when().post(s"/api/tournament/$id/join").`then`().statusCode(409)

  @Test
  def joinedBotCanWithdraw(): Unit =
    val id = createTournament(directorToken("dir-wd"), "WdTour")
    val bt = botToken("wdbot-1", "WdBot1")
    botJoin(id, "wdbot-1", "WdBot1")
    authed(bt)
      .when()
      .post(s"/api/tournament/$id/withdraw")
      .`then`()
      .statusCode(200)
      .body("ok", is(true))

  @Test
  def notJoinedBotReturns409OnWithdraw(): Unit =
    val id = createTournament(directorToken("dir-wdnj"), "WdnjTour")
    val bt = botToken("wdnjbot-1", "WdnjBot1")
    authed(bt).when().post(s"/api/tournament/$id/withdraw").`then`().statusCode(409)

  @Test
  def directorStartsWith2Bots(): Unit =
    val token = directorToken("dir-start")
    val id    = createTournament(token, "StartTour2")
    botJoin(id, "stbot-1", "StBot1")
    botJoin(id, "stbot-2", "StBot2")
    postAndCheck(token, s"/api/tournament/$id/start", 200)

  @Test
  def nonDirectorReturns403OnStart(): Unit =
    val id = createTournament(directorToken("dir-ndstart"), "NdStartTour")
    botJoin(id, "ndstbot-1", "NdstBot1")
    botJoin(id, "ndstbot-2", "NdstBot2")
    postAndCheck(directorToken("other-ndstart"), s"/api/tournament/$id/start", 403)

  @Test
  def fewerThan2BotsReturns409OnStart(): Unit =
    val token = directorToken("dir-1bot")
    val id    = createTournament(token, "1BotTour")
    botJoin(id, "onebot-1", "OneBot1")
    postAndCheck(token, s"/api/tournament/$id/start", 409)

  @Test
  def resultsReturns200WithNdjsonContentType(): Unit =
    val id = createTournament(directorToken("dir-res"), "ResTour")
    RestAssured
      .`given`()
      .when()
      .get(s"/api/tournament/$id/results")
      .`then`()
      .statusCode(200)
      .contentType("application/x-ndjson")

  @Test
  def returnsPairingsForRoundAfterStart(): Unit =
    val token = directorToken("dir-round")
    val id    = createTournament(token, "RoundTour")
    botJoin(id, "rndbot-1", "RndBot1")
    botJoin(id, "rndbot-2", "RndBot2")
    startTournament(token, id)
    RestAssured.`given`().when().get(s"/api/tournament/$id/round/1").`then`().statusCode(200)

  @Test
  def returns404ForUnknownTournamentRound(): Unit =
    RestAssured.`given`().when().get("/api/tournament/XXXXXX/round/1").`then`().statusCode(404)

  @Test
  def returnsPgnByDefault(): Unit =
    val id = createTournament(directorToken("dir-pgn"), "PgnTour")
    RestAssured.`given`().when().get(s"/api/tournament/$id/export/games").`then`().statusCode(200)

  @Test
  def returnsNdjsonWhenAcceptApplicationXNdjson(): Unit =
    val id = createTournament(directorToken("dir-ndjson"), "NdjsonTour")
    RestAssured
      .`given`()
      .header("Accept", "application/x-ndjson")
      .when()
      .get(s"/api/tournament/$id/export/games")
      .`then`()
      .statusCode(200)
      .contentType("application/x-ndjson")
