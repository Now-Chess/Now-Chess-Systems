package de.nowchess.analysis.service

import de.nowchess.analysis.client.{ChessApiClient, ChessApiRequestDto}
import de.nowchess.analysis.dto.{AnalysisRequestDto, AnalysisResponseDto}
import de.nowchess.analysis.error.{AnalysisUpstreamException, InvalidFenException}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger

import scala.compiletime.uninitialized

@ApplicationScoped
class AnalysisService:

  private val log = Logger.getLogger(classOf[AnalysisService])

  private val DefaultDepth = 12
  private val MinDepth     = 1
  private val MaxDepth     = 99

  // scalafix:off DisableSyntax.var
  @Inject
  @RestClient
  var chessApiClient: ChessApiClient = uninitialized
  // scalafix:on DisableSyntax.var

  // scalafix:off DisableSyntax.throw
  def analyse(request: AnalysisRequestDto): AnalysisResponseDto =
    val fen = request.fen.trim
    if fen.isEmpty then throw InvalidFenException(fen)
    validateFen(fen)

    val depth = request.depth
      .map(d => d.max(MinDepth).min(MaxDepth))
      .getOrElse(DefaultDepth)

    log.debugf("Analysing FEN '%s' at depth %d", fen, depth)

    val apiResponse =
      try chessApiClient.analyse(ChessApiRequestDto(fen, depth))
      catch
        case ex: Exception =>
          log.warnf(ex, "Chess API call failed for FEN '%s'", fen)
          throw AnalysisUpstreamException(ex)

    val continuationMoves = apiResponse.pv
      .map(_.split(" ").toList.filter(_.nonEmpty))
      .getOrElse(List.empty)

    AnalysisResponseDto(
      fen = fen,
      depth = apiResponse.depth.getOrElse(depth),
      bestMove = apiResponse.move,
      evaluation = apiResponse.centipawns,
      mate = apiResponse.mate,
      continuationMoves = continuationMoves,
    )
  // scalafix:on DisableSyntax.throw

  /** Rudimentary FEN structure validation — checks the board part has 8 ranks. */
  // scalafix:off DisableSyntax.throw
  private def validateFen(fen: String): Unit =
    val parts = fen.split(" ")
    if parts.length < 1 then throw InvalidFenException(fen)
    val ranks = parts(0).split("/")
    if ranks.length != 8 then throw InvalidFenException(fen)
  // scalafix:on DisableSyntax.throw
