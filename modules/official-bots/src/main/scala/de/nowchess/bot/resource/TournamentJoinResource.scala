package de.nowchess.bot.resource

import de.nowchess.bot.service.TournamentBotGamePlayer
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}
import org.jboss.logging.Logger
import scala.compiletime.uninitialized

@Path("/api/bots/official")
@ApplicationScoped
@RolesAllowed(Array("**"))
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class TournamentJoinResource:

  private val log = Logger.getLogger(classOf[TournamentJoinResource])

  // scalafix:off DisableSyntax.var
  @Inject var player: TournamentBotGamePlayer = uninitialized
  // scalafix:on DisableSyntax.var

  @POST
  @Path("/join-tournament")
  def joinTournament(req: JoinTournamentRequest): Response =
    val serverUrl  = req.serverUrl.filter(_.nonEmpty).getOrElse(player.defaultServerUrl)
    val difficulty = if req.difficulty.nonEmpty then req.difficulty else "medium"
    log.infof(
      "Official bot join requested — tournament=%s difficulty=%s server=%s",
      req.tournamentId,
      difficulty,
      serverUrl,
    )
    player.joinTournament(req.tournamentId, req.botToken, difficulty, serverUrl) match
      case Right(botId) =>
        val resp = JoinTournamentResponse(botId, difficulty, "joining")
        Response.ok(resp).build()
      case Left(err) =>
        Response
          .status(Response.Status.BAD_GATEWAY)
          .entity(s"""{"error":"$err"}""")
          .build()
