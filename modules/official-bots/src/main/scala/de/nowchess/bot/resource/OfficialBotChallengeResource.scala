package de.nowchess.bot.resource

import de.nowchess.bot.service.DifficultyMapper
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}

@Path("/api/challenge/official")
@ApplicationScoped
@RolesAllowed(Array("**"))
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class OfficialBotChallengeResource:

  @POST
  @Path("/{botId}")
  def challengeWithDifficulty(
      @PathParam("botId") botId: String,
      @QueryParam("difficulty") difficulty: Int,
  ): Response =
    DifficultyMapper.fromElo(difficulty) match
      case None =>
        Response
          .status(Response.Status.BAD_REQUEST)
          .entity(s"""{"error":"difficulty must be between 1000 and 2800"}""")
          .build()
      case Some(botDifficulty) =>
        // TODO: wire to account service challenge creation + bot routing
        Response
          .status(Response.Status.CREATED)
          .entity(s"""{"botId":"$botId","difficulty":$difficulty,"status":"pending"}""")
          .build()
