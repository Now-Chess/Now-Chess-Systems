package de.nowchess.account.client

import de.nowchess.security.{InternalClientHeadersFactory, InternalSecretClientFilter}
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.annotation.{RegisterClientHeaders, RegisterProvider}
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

case class CorePlayerInfo(id: String, displayName: String)
case class CoreTimeControl(limitSeconds: Option[Int], incrementSeconds: Option[Int], daysPerMove: Option[Int])
case class CoreCreateGameRequest(
    white: Option[CorePlayerInfo],
    black: Option[CorePlayerInfo],
    timeControl: Option[CoreTimeControl],
    mode: Option[String],
)
case class CoreGameResponse(gameId: String)

@Path("/api/board/game")
@RegisterRestClient(configKey = "core-service")
@RegisterProvider(classOf[InternalSecretClientFilter])
@RegisterClientHeaders(classOf[InternalClientHeadersFactory])
trait CoreGameClient:

  @POST
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def createGame(req: CoreCreateGameRequest): CoreGameResponse
