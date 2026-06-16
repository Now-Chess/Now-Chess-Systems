package de.nowchess.analysis.client

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

/** MicroProfile REST client for chess-api.com v1.
  *
  * Base URL is resolved from `quarkus.rest-client.chess-api.url` in application.yml.
  */
@Path("/")
@RegisterRestClient(configKey = "chess-api")
trait ChessApiClient:

  @POST
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def analyse(body: ChessApiRequestDto): ChessApiResponseDto
