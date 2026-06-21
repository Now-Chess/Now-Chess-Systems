package de.nowchess.tournament.resource

import de.nowchess.tournament.dto.ExternalTournamentServerList
import de.nowchess.tournament.service.TournamentServerRegistry
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}
import scala.compiletime.uninitialized

@Path("/api/tournament/servers")
@ApplicationScoped
@RolesAllowed(Array("**"))
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class TournamentServerResource:

  // scalafix:off DisableSyntax.var
  @Inject var registry: TournamentServerRegistry = uninitialized
  // scalafix:on

  @GET
  def list(): Response =
    Response.ok(ExternalTournamentServerList(registry.list())).build()
