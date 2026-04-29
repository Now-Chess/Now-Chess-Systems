package de.nowchess.chess.client

import de.nowchess.security.InternalSecretClientFilter
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey = "store-service")
@RegisterProvider(classOf[InternalSecretClientFilter])
@Path("/game")
trait StoreServiceClient:
  @GET
  @Path("/{gameId}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def getGame(@PathParam("gameId") gameId: String): GameRecordDto
