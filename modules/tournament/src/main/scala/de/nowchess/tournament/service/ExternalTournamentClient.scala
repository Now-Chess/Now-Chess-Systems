package de.nowchess.tournament.service

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
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
  // scalafix:on

  private def buildClient(): Client = ClientBuilder.newClient()

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
