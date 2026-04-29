package de.nowchess.store.resource

import de.nowchess.store.repository.GameRecordRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}
import jakarta.ws.rs.DefaultValue
import scala.compiletime.uninitialized

@Path("/game")
@ApplicationScoped
class StoreGameResource:
  @Inject
  // scalafix:off DisableSyntax.var
  var repository: GameRecordRepository = uninitialized
  // scalafix:on

  @GET
  @Path("/{gameId}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def getGame(@PathParam("gameId") gameId: String): Response =
    repository
      .findByGameId(gameId)
      .fold(Response.status(404).build())(r => Response.ok(r).build())

  @GET
  @Path("/running/{playerId}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def getRunning(
      @PathParam("playerId") playerId: String,
      @QueryParam("offset") @DefaultValue("0") offset: Int,
      @QueryParam("limit") @DefaultValue("20") limit: Int,
  ): Response =
    Response.ok(repository.findByPlayerIdRunning(playerId, offset, limit)).build()

  @GET
  @Path("/history/{playerId}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def getHistory(
      @PathParam("playerId") playerId: String,
      @QueryParam("offset") @DefaultValue("0") offset: Int,
      @QueryParam("limit") @DefaultValue("20") limit: Int,
  ): Response =
    Response.ok(repository.findByPlayerId(playerId, offset, limit)).build()
