package de.nowchess.tournament.service

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import de.nowchess.tournament.dto.ReplicateTournamentRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.client.{Client, ClientBuilder, Entity}
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.config.inject.ConfigProperty
import scala.compiletime.uninitialized
import scala.util.Try

@ApplicationScoped
class ExternalTournamentClient:

  // scalafix:off DisableSyntax.var
  @Inject var objectMapper: ObjectMapper              = uninitialized
  @volatile private var directorToken: Option[String] = None

  @ConfigProperty(name = "nowchess.tournament.native-server-url", defaultValue = "http://141.37.123.132:8086")
  var nativeServerUrl: String = uninitialized

  @ConfigProperty(name = "nowchess.tournament.director-name", defaultValue = "NowChess System")
  var directorName: String = uninitialized
  // scalafix:on

  private def buildClient(): Client = ClientBuilder.newClient()

  // The tournament-server only accepts HS256 tokens it issued. Never forward a NowChessSystems
  // RS256 user token to it — swap in the director token registered on that server.
  private def normalize(url: String): String = url.stripSuffix("/")

  def isNativeServer(serverUrl: String): Boolean =
    nativeServerUrl.nonEmpty && normalize(serverUrl) == normalize(nativeServerUrl)

  private def directorBearer(): Option[String] =
    directorToken
      .orElse {
        val fresh = registerDirector(nativeServerUrl, directorName)
        directorToken = fresh
        fresh
      }
      .map(t => s"Bearer $t")

  private def authFor(serverUrl: String, userAuth: Option[String]): Option[String] =
    if isNativeServer(serverUrl) then directorBearer() else userAuth

  def publishNative(serverUrl: String, directorName: String, form: String): Option[String] =
    val token = directorToken.orElse {
      val fresh = registerDirector(serverUrl, directorName)
      directorToken = fresh
      fresh
    }
    token.flatMap { tok =>
      createNative(serverUrl, tok, form).orElse {
        val refreshed = registerDirector(serverUrl, directorName)
        directorToken = refreshed
        refreshed.flatMap(createNative(serverUrl, _, form))
      }
    }

  private def registerDirector(serverUrl: String, name: String): Option[String] =
    Try {
      val client = buildClient()
      val body   = s"""{"name":"${name.replace("\"", "\\\"")}","isBot":false}"""
      val response = client
        .target(s"$serverUrl/api/auth/register")
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.entity(body, MediaType.APPLICATION_JSON))
      try
        if response.getStatus / 100 == 2 then
          Option(objectMapper.readTree(response.readEntity(classOf[String])).path("token").asText()).filter(_.nonEmpty)
        else None
      finally
        response.close()
        client.close()
    }.getOrElse(None)

  private def createNative(serverUrl: String, token: String, form: String): Option[String] =
    Try {
      val client = buildClient()
      val response = client
        .target(s"$serverUrl/api/tournament")
        .request(MediaType.APPLICATION_JSON)
        .header("Authorization", s"Bearer $token")
        .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED))
      try
        if response.getStatus / 100 == 2 then
          Option(objectMapper.readTree(response.readEntity(classOf[String])).path("id").asText()).filter(_.nonEmpty)
        else None
      finally
        response.close()
        client.close()
    }.getOrElse(None)

  def fetchList(serverUrl: String): Option[JsonNode] =
    Try {
      val client   = buildClient()
      val response = client.target(s"$serverUrl/api/tournament").request(MediaType.APPLICATION_JSON).get()
      try
        if response.getStatus == 200 then Some(objectMapper.readTree(response.readEntity(classOf[String])))
        else None
      finally
        response.close()
        client.close()
    }.getOrElse(None)

  def fetch(serverUrl: String, id: String): Option[JsonNode] =
    Try {
      val client   = buildClient()
      val response = client.target(s"$serverUrl/api/tournament/$id").request(MediaType.APPLICATION_JSON).get()
      try
        if response.getStatus == 200 then Some(objectMapper.readTree(response.readEntity(classOf[String])))
        else None
      finally
        response.close()
        client.close()
    }.getOrElse(None)

  def fetchPairings(serverUrl: String, id: String, round: Int): Option[JsonNode] =
    Try {
      val client = buildClient()
      val response =
        client.target(s"$serverUrl/api/tournament/$id/round/$round").request(MediaType.APPLICATION_JSON).get()
      try
        if response.getStatus == 200 then Some(objectMapper.readTree(response.readEntity(classOf[String])))
        else None
      finally
        response.close()
        client.close()
    }.getOrElse(None)

  def proxyPost(serverUrl: String, path: String, authHeader: Option[String]): (Int, String) =
    Try {
      val client   = buildClient()
      val builder  = client.target(s"$serverUrl/$path").request(MediaType.APPLICATION_JSON)
      val withAuth = authFor(serverUrl, authHeader).fold(builder)(h => builder.header("Authorization", h))
      val response = withAuth.post(Entity.json(""))
      try (response.getStatus, response.readEntity(classOf[String]))
      finally
        response.close()
        client.close()
    }.getOrElse((502, """{"error":"External server unreachable"}"""))

  def replicateTournament(serverUrl: String, req: ReplicateTournamentRequest, selfUrl: String): Boolean =
    Try {
      val client = buildClient()
      val body   = objectMapper.writeValueAsString(req)
      val response = client
        .target(s"$serverUrl/api/tournament/replicate")
        .request(MediaType.APPLICATION_JSON)
        .header("X-Origin-Url", selfUrl)
        .post(Entity.entity(body, MediaType.APPLICATION_JSON))
      try response.getStatus / 100 == 2
      finally
        response.close()
        client.close()
    }.getOrElse(false)

  def proxyGetStream(serverUrl: String, path: String, authHeader: Option[String]): Option[java.io.InputStream] =
    Try {
      val client   = buildClient()
      val builder  = client.target(s"$serverUrl/$path").request("application/x-ndjson")
      val withAuth = authFor(serverUrl, authHeader).fold(builder)(h => builder.header("Authorization", h))
      val response = withAuth.get()
      if response.getStatus == 200 then Some(response.readEntity(classOf[java.io.InputStream]))
      else
        response.close()
        client.close()
        None
    }.getOrElse(None)
