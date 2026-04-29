package de.nowchess.io.service.resource

import de.nowchess.api.game.GameContext
import de.nowchess.security.InternalOnly
import de.nowchess.io.fen.{FenExporter, FenParser}
import de.nowchess.io.pgn.{PgnExporter, PgnParser}
import de.nowchess.io.service.dto.{CombinedExportResponse, ImportFenRequest, ImportPgnRequest, IoErrorDto}
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.{Content, Schema}
import org.eclipse.microprofile.openapi.annotations.responses.{APIResponse, APIResponses}
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Path("/io")
@ApplicationScoped
@InternalOnly
@Tag(name = "IO", description = "Chess notation import and export")
class IoResource:

  @POST
  @Path("/import/fen")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(summary = "Import FEN", description = "Parse a FEN string into a GameContext")
  @APIResponses(
    Array(
      new APIResponse(responseCode = "200", description = "Parsed GameContext"),
      new APIResponse(responseCode = "400", description = "Invalid FEN"),
    ),
  )
  def importFen(body: ImportFenRequest): Uni[Response] =
    Uni.createFrom().item {
      FenParser.parseFen(body.fen) match
        case Left(err) =>
          Response.status(400).entity(IoErrorDto("INVALID_FEN", err.message)).build()
        case Right(ctx) =>
          Response.ok(ctx).build()
    }

  @POST
  @Path("/import/pgn")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(summary = "Import PGN", description = "Parse a PGN string into a GameContext")
  @APIResponses(
    Array(
      new APIResponse(responseCode = "200", description = "Parsed GameContext"),
      new APIResponse(responseCode = "400", description = "Invalid PGN"),
    ),
  )
  def importPgn(body: ImportPgnRequest): Uni[Response] =
    Uni.createFrom().item {
      PgnParser.importGameContext(body.pgn) match
        case Left(err) =>
          Response.status(400).entity(IoErrorDto("INVALID_PGN", err.message)).build()
        case Right(ctx) =>
          Response.ok(ctx).build()
    }

  @POST
  @Path("/export/fen")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.TEXT_PLAIN))
  @Operation(summary = "Export FEN", description = "Serialize a GameContext to FEN notation")
  @APIResponse(responseCode = "200", description = "FEN string")
  def exportFen(ctx: GameContext): Uni[Response] =
    Uni.createFrom().item(Response.ok(FenExporter.exportGameContext(ctx)).build())

  @POST
  @Path("/export/pgn")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array("application/x-chess-pgn"))
  @Operation(summary = "Export PGN", description = "Serialize a GameContext to PGN notation")
  @APIResponse(responseCode = "200", description = "PGN text")
  def exportPgn(ctx: GameContext): Uni[Response] =
    Uni.createFrom().item(Response.ok(PgnExporter.exportGameContext(ctx)).build())

  @POST
  @Path("/export/combined")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(summary = "Export FEN and PGN", description = "Serialize a GameContext to both FEN and PGN in one call")
  @APIResponse(responseCode = "200", description = "FEN and PGN")
  def exportCombined(ctx: GameContext): Uni[Response] =
    Uni
      .createFrom()
      .item(
        Response
          .ok(CombinedExportResponse(FenExporter.exportGameContext(ctx), PgnExporter.exportGameContext(ctx)))
          .build(),
      )
