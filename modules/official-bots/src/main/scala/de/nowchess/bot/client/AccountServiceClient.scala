package de.nowchess.bot.client

import de.nowchess.security.{InternalClientHeadersFactory, InternalSecretClientFilter}
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.annotation.{RegisterClientHeaders, RegisterProvider}
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

case class SyncOfficialBotsRequest(bots: List[String])

@Path("/api/account/official-bots")
@RegisterRestClient(configKey = "account-service")
@RegisterProvider(classOf[InternalSecretClientFilter])
@RegisterClientHeaders(classOf[InternalClientHeadersFactory])
trait AccountServiceClient:

  @POST
  @Path("/sync")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  def syncBots(req: SyncOfficialBotsRequest): Unit
