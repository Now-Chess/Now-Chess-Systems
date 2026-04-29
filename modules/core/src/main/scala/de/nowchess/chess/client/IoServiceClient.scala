package de.nowchess.chess.client

import de.nowchess.api.dto.{ImportFenRequest, ImportPgnRequest}
import de.nowchess.api.game.GameContext
import de.nowchess.security.InternalSecretClientFilter
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

case class CombinedExportResponse(fen: String, pgn: String)

@Path("/io")
@RegisterRestClient(configKey = "io-service")
@RegisterProvider(classOf[InternalSecretClientFilter])
trait IoServiceClient:

  @POST
  @Path("/import/fen")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def importFen(body: ImportFenRequest): GameContext

  @POST
  @Path("/import/pgn")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def importPgn(body: ImportPgnRequest): GameContext

  @POST
  @Path("/export/fen")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.TEXT_PLAIN))
  def exportFen(ctx: GameContext): String

  @POST
  @Path("/export/pgn")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array("application/x-chess-pgn"))
  def exportPgn(ctx: GameContext): String

  @POST
  @Path("/export/combined")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def exportCombined(ctx: GameContext): CombinedExportResponse
