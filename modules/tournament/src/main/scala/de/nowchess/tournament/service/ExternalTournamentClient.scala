package de.nowchess.tournament.service

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import de.nowchess.tournament.dto.ReplicateTournamentRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.client.{Client, ClientBuilder, Entity}
import jakarta.ws.rs.core.MediaType
import scala.compiletime.uninitialized
import scala.util.Try

@ApplicationScoped
class ExternalTournamentClient:

  // scalafix:off DisableSyntax.var
  @Inject var objectMapper: ObjectMapper = uninitialized
  @volatile private var directorToken: Option[String] = None
  // scalafix:on

  private def buildClient(): Client = ClientBuilder.newClient()

  def publishNative(serverUrl: String, directorName: String, form: String): Boolean =
    val token = directorToken.orElse {
      val fresh = registerDirector(serverUrl, directorName)
      directorToken = fresh
      fresh
    }
    token.exists { tok =>
      if createNative(serverUrl, tok, form) then true
      else
        val refreshed = registerDirector(serverUrl, directorName)
        directorToken = refreshed
        refreshed.exists(createNative(serverUrl, _, form))
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

  private def createNative(serverUrl: String, token: String, form: String): Boolean =
    Try {
      val client = buildClient()
      val response = client
        .target(s"$serverUrl/api/tournament")
        .request(MediaType.APPLICATION_JSON)
        .header("Authorization", s"Bearer $token")
        .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED))
      try response.getStatus / 100 == 2
      finally
        response.close()
        client.close()
    }.getOrElse(false)

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
      val withAuth = authHeader.fold(builder)(h => builder.header("Authorization", h))
      val response = withAuth.post(Entity.json(""))
      try (response.getStatus, response.readEntity(classOf[String]))
      finally
        response.close()
        client.close()
    }.getOrElse((502, """{"error":"External server unreachable"}"""))

  def replicateTournament(serverUrl: String, req: ReplicateTournamentRequest, selfUrl: String): Boolean =
    Try {
      val client  = buildClient()
      val body    = objectMapper.writeValueAsString(req)
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
      val withAuth = authHeader.fold(builder)(h => builder.header("Authorization", h))
      val response = withAuth.get()
      if response.getStatus == 200 then Some(response.readEntity(classOf[java.io.InputStream]))
      else
        response.close()
        client.close()
        None
    }.getOrElse(None)
