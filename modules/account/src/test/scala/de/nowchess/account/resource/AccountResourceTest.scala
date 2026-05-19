package de.nowchess.account.resource

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test

@QuarkusTest
class AccountResourceTest:

  private def givenRequest() = RestAssured.`given`().contentType(ContentType.JSON)

  private def registerBody(username: String, email: String = "", password: String = "secret") =
    val resolvedEmail = if email.isEmpty then s"$username@example.com" else email
    s"""{"username":"$username","email":"$resolvedEmail","password":"$password"}"""

  private def loginBody(username: String, password: String = "secret") =
    s"""{"username":"$username","password":"$password"}"""

  private def registerAndLogin(username: String): String =
    givenRequest()
      .body(registerBody(username))
      .when()
      .post("/api/account")
      .`then`()
      .statusCode(200)
    givenRequest()
      .body(loginBody(username))
      .when()
      .post("/api/account/login")
      .`then`()
      .statusCode(200)
      .extract()
      .path[String]("accessToken")

  private def registerAndLoginPair(username: String): (String, String) =
    givenRequest()
      .body(registerBody(username))
      .when()
      .post("/api/account")
      .`then`()
      .statusCode(200)
    val resp = givenRequest()
      .body(loginBody(username))
      .when()
      .post("/api/account/login")
      .`then`()
      .statusCode(200)
      .extract()
      .response()
    (resp.path[String]("accessToken"), resp.path[String]("refreshToken"))

  @Test
  def registerReturns200(): Unit =
    givenRequest()
      .body(registerBody("alice"))
      .when()
      .post("/api/account")
      .`then`()
      .statusCode(200)
      .body("username", is("alice"))
      .body("rating", is(1500))

  @Test
  def registerConflictOnDuplicateUsername(): Unit =
    givenRequest().body(registerBody("bob")).when().post("/api/account")
    givenRequest()
      .body(registerBody("bob"))
      .when()
      .post("/api/account")
      .`then`()
      .statusCode(409)
      .body("error", containsString("bob"))

  @Test
  def loginReturns200WithTokenPair(): Unit =
    givenRequest().body(registerBody("charlie")).when().post("/api/account")
    givenRequest()
      .body(loginBody("charlie"))
      .when()
      .post("/api/account/login")
      .`then`()
      .statusCode(200)
      .body("accessToken", notNullValue())
      .body("refreshToken", notNullValue())

  @Test
  def loginUnauthorizedOnWrongPassword(): Unit =
    givenRequest().body(registerBody("dave")).when().post("/api/account")
    givenRequest()
      .body(loginBody("dave", "wrongpassword"))
      .when()
      .post("/api/account/login")
      .`then`()
      .statusCode(401)

  @Test
  def getMeReturns200(): Unit =
    val token = registerAndLogin("eve")
    givenRequest()
      .header("Authorization", s"Bearer $token")
      .when()
      .get("/api/account/me")
      .`then`()
      .statusCode(200)
      .body("username", is("eve"))

  @Test
  def getPublicProfileReturns200(): Unit =
    givenRequest().body(registerBody("frank")).when().post("/api/account")
    givenRequest()
      .when()
      .get("/api/account/frank")
      .`then`()
      .statusCode(200)
      .body("username", is("frank"))

  @Test
  def getPublicProfileNotFound(): Unit =
    givenRequest()
      .when()
      .get("/api/account/doesnotexist")
      .`then`()
      .statusCode(404)

  @Test
  def refreshReturnsNewTokenPair(): Unit =
    val (_, refreshToken) = registerAndLoginPair("refresh_user")
    givenRequest()
      .body(s"""{"refreshToken":"$refreshToken"}""")
      .when()
      .post("/api/account/refresh")
      .`then`()
      .statusCode(200)
      .body("accessToken", notNullValue())
      .body("refreshToken", notNullValue())

  @Test
  def refreshWithInvalidTokenReturns401(): Unit =
    givenRequest()
      .body("""{"refreshToken":"invalid.token.value"}""")
      .when()
      .post("/api/account/refresh")
      .`then`()
      .statusCode(401)

  @Test
  def refreshWithAccessTokenReturns401(): Unit =
    val accessToken = registerAndLogin("refresh_bad_type")
    givenRequest()
      .body(s"""{"refreshToken":"$accessToken"}""")
      .when()
      .post("/api/account/refresh")
      .`then`()
      .statusCode(401)
