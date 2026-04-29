package de.nowchess.account.resource

import de.nowchess.account.dto.*
import de.nowchess.account.error.ChallengeError
import de.nowchess.account.service.ChallengeService
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}
import org.eclipse.microprofile.jwt.JsonWebToken
import scala.compiletime.uninitialized

import java.util.UUID

@Path("/api/challenge")
@ApplicationScoped
@RolesAllowed(Array("**"))
@Consumes(Array(MediaType.APPLICATION_JSON))
@Produces(Array(MediaType.APPLICATION_JSON))
class ChallengeResource:

  // scalafix:off DisableSyntax.var
  @Inject
  var challengeService: ChallengeService = uninitialized

  @Inject
  var jwt: JsonWebToken = uninitialized
  // scalafix:on

  @POST
  @Path("/{username}")
  def create(@PathParam("username") username: String, req: ChallengeRequest): Response =
    val userId = UUID.fromString(jwt.getSubject)
    challengeService.create(userId, username, req) match
      case Right(challenge) =>
        Response.status(Response.Status.CREATED).entity(challengeService.toDto(challenge)).build()
      case Left(error) =>
        val status = error match
          case ChallengeError.UserNotFound(_) | ChallengeError.ChallengerNotFound => Response.Status.NOT_FOUND
          case ChallengeError.CannotChallengeSelf                                 => Response.Status.BAD_REQUEST
          case _                                                                  => Response.Status.CONFLICT
        Response.status(status).entity(ErrorDto(error.message)).build()

  @GET
  def list(): Response =
    val userId = UUID.fromString(jwt.getSubject)
    Response.ok(challengeService.listForUser(userId)).build()

  @GET
  @Path("/{id}")
  def get(@PathParam("id") id: UUID): Response =
    val userId = UUID.fromString(jwt.getSubject)
    challengeService.findById(id, userId) match
      case Right(challenge) => Response.ok(challengeService.toDto(challenge)).build()
      case Left(error)      => errorResponse(error)

  @POST
  @Path("/{id}/accept")
  def accept(@PathParam("id") id: UUID): Response =
    val userId = UUID.fromString(jwt.getSubject)
    challengeService.accept(id, userId) match
      case Right(challenge) => Response.ok(challengeService.toDto(challenge)).build()
      case Left(error)      => errorResponse(error)

  @POST
  @Path("/{id}/decline")
  def decline(@PathParam("id") id: UUID, req: DeclineRequest): Response =
    val userId = UUID.fromString(jwt.getSubject)
    challengeService.decline(id, userId, req) match
      case Right(challenge) => Response.ok(challengeService.toDto(challenge)).build()
      case Left(error)      => errorResponse(error)

  @POST
  @Path("/{id}/cancel")
  def cancel(@PathParam("id") id: UUID): Response =
    val userId = UUID.fromString(jwt.getSubject)
    challengeService.cancel(id, userId) match
      case Right(challenge) => Response.ok(challengeService.toDto(challenge)).build()
      case Left(error)      => errorResponse(error)

  private def errorResponse(error: ChallengeError): Response =
    val status = error match
      case ChallengeError.ChallengeNotFound  => Response.Status.NOT_FOUND
      case ChallengeError.NotAuthorized      => Response.Status.FORBIDDEN
      case ChallengeError.GameCreationFailed => Response.Status.INTERNAL_SERVER_ERROR
      case _                                 => Response.Status.BAD_REQUEST
    Response.status(status).entity(ErrorDto(error.message)).build()
