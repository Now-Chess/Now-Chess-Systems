package de.nowchess.analysis.resource

import de.nowchess.analysis.dto.{AnalysisRequestDto, AnalysisResponseDto}
import de.nowchess.analysis.service.AnalysisService
import jakarta.annotation.security.PermitAll
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}

import scala.compiletime.uninitialized

@Path("/api/analysis")
@ApplicationScoped
class AnalysisResource:

  // scalafix:off DisableSyntax.var
  @Inject
  var analysisService: AnalysisService = uninitialized
  // scalafix:on DisableSyntax.var

  /** Analyse a chess position.
    *
    * Accepts a FEN string and optional depth, proxies to chess-api.com, and returns structured analysis data.
    */
  @POST
  @Path("/position")
  @PermitAll
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def analysePosition(body: AnalysisRequestDto): Response =
    val result = analysisService.analyse(body)
    Response.ok(result).build()
