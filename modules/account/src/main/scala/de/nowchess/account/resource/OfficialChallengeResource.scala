package de.nowchess.account.resource

import de.nowchess.account.client.{CoreCreateGameRequest, CoreGameClient, CorePlayerInfo}
import de.nowchess.account.dto.{ErrorDto, OfficialChallengeResponse}
import de.nowchess.account.service.{AccountService, EventPublisher}
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}
import org.eclipse.microprofile.jwt.JsonWebToken
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.logging.Logger
import scala.compiletime.uninitialized

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

@Path("/api/challenge/official")
@ApplicationScoped
@RolesAllowed(Array("**"))
@Consumes(Array(MediaType.APPLICATION_JSON))
@Produces(Array(MediaType.APPLICATION_JSON))
class OfficialChallengeResource:

  // scalafix:off DisableSyntax.var
  @Inject var accountService: AccountService    = uninitialized
  @Inject var jwt: JsonWebToken                 = uninitialized
  @Inject var botEventPublisher: EventPublisher = uninitialized

  @Inject
  @RestClient
  var coreGameClient: CoreGameClient = uninitialized
  // scalafix:on

  private val log = Logger.getLogger(classOf[OfficialChallengeResource])

  @POST
  @Path("/{botName}")
  def challengeWithDifficulty(
      @PathParam("botName") botName: String,
      @QueryParam("difficulty") difficulty: Int,
      @QueryParam("color") color: String,
  ): Response =
    if difficulty < 1000 || difficulty > 2800 then
      Response
        .status(Response.Status.BAD_REQUEST)
        .entity(ErrorDto("difficulty must be between 1000 and 2800"))
        .build()
    else
      val normalizedColor = Option(color).map(_.toLowerCase).getOrElse("random")
      normalizedColor match
        case "white" | "black" | "random" =>
          val userId  = UUID.fromString(jwt.getSubject)
          val botOpt  = accountService.getOfficialBotAccounts().find(_.name == botName)
          val userOpt = accountService.findById(userId)

          (botOpt, userOpt) match
            case (None, _) =>
              Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(s"Official bot '$botName' not found")).build()
            case (_, None) =>
              Response.status(Response.Status.NOT_FOUND).entity(ErrorDto("User not found")).build()
            case (Some(bot), Some(user)) =>
              val userIsWhite = normalizedColor match
                case "white" => true
                case "black" => false
                case _       => ThreadLocalRandom.current().nextBoolean()
              val (white, black, botColor) =
                if userIsWhite then
                  (CorePlayerInfo(user.id.toString, user.username), CorePlayerInfo(bot.id.toString, bot.name), "black")
                else
                  (CorePlayerInfo(bot.id.toString, bot.name), CorePlayerInfo(user.id.toString, user.username), "white")
              val req = CoreCreateGameRequest(Some(white), Some(black), None, Some("Authenticated"))
              val gameId =
                try Right(coreGameClient.createGame(req).gameId)
                catch case _ => Left("Failed to create game")
              gameId match
                case Left(err) =>
                  Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ErrorDto(err)).build()
                case Right(id) =>
                  try botEventPublisher.publishGameStart(bot.name, id, botColor, difficulty, bot.id.toString)
                  catch case ex: Exception => log.warnf(ex, "Failed to notify bot for game %s", id)
                  Response
                    .status(Response.Status.CREATED)
                    .entity(OfficialChallengeResponse(id, botName, difficulty))
                    .build()
        case other =>
          Response
            .status(Response.Status.BAD_REQUEST)
            .entity(ErrorDto(s"Invalid color: $other. Must be white, black or random"))
            .build()
