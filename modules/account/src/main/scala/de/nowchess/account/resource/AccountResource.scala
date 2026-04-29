package de.nowchess.account.resource

import de.nowchess.account.domain.{BotAccount, OfficialBotAccount, UserAccount}
import de.nowchess.account.dto.*
import de.nowchess.account.error.AccountError
import de.nowchess.account.service.AccountService
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}
import org.eclipse.microprofile.jwt.JsonWebToken
import scala.compiletime.uninitialized

import java.util.UUID

@Path("/api/account")
@ApplicationScoped
@Consumes(Array(MediaType.APPLICATION_JSON))
@Produces(Array(MediaType.APPLICATION_JSON))
class AccountResource:

  // scalafix:off DisableSyntax.var
  @Inject
  var accountService: AccountService = uninitialized

  @Inject
  var jwt: JsonWebToken = uninitialized
  // scalafix:on

  @POST
  def register(req: RegisterRequest): Response =
    accountService.register(req) match
      case Right(account) =>
        Response.ok(toPublicDto(account)).build()
      case Left(error) =>
        Response.status(Response.Status.CONFLICT).entity(ErrorDto(error.message)).build()

  @POST
  @Path("/login")
  def login(req: LoginRequest): Response =
    accountService.login(req) match
      case Right(token) =>
        Response.ok(TokenResponse(token)).build()
      case Left(AccountError.UserBanned) =>
        Response.status(Response.Status.FORBIDDEN).entity(ErrorDto(AccountError.UserBanned.message)).build()
      case Left(error) =>
        Response.status(Response.Status.UNAUTHORIZED).entity(ErrorDto(error.message)).build()

  @GET
  @Path("/me")
  @RolesAllowed(Array("**"))
  def me(): Response =
    val id = UUID.fromString(jwt.getSubject)
    accountService.findById(id) match
      case Some(account) => Response.ok(toPublicDto(account)).build()
      case None          => Response.status(Response.Status.NOT_FOUND).build()

  @GET
  @Path("/{username}")
  def publicProfile(@PathParam("username") username: String): Response =
    accountService.findByUsername(username) match
      case Some(account) => Response.ok(toPublicDto(account)).build()
      case None          => Response.status(Response.Status.NOT_FOUND).build()

  @POST
  @Path("/{userId}/ban")
  @RolesAllowed(Array("Admin"))
  def banUser(@PathParam("userId") userId: String): Response =
    accountService.banUser(UUID.fromString(userId)) match
      case Right(user) => Response.ok(toPublicDto(user)).build()
      case Left(error) =>
        Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(error.message)).build()

  @POST
  @Path("/{userId}/unban")
  @RolesAllowed(Array("Admin"))
  def unbanUser(@PathParam("userId") userId: String): Response =
    accountService.unbanUser(UUID.fromString(userId)) match
      case Right(user) => Response.ok(toPublicDto(user)).build()
      case Left(error) =>
        Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(error.message)).build()

  @POST
  @Path("/bots")
  @RolesAllowed(Array("**"))
  def createBotAccount(req: CreateBotAccountRequest): Response =
    val ownerId = UUID.fromString(jwt.getSubject)
    accountService.createBotAccount(ownerId, req.name) match
      case Right(bot) =>
        Response.status(Response.Status.CREATED).entity(toBotDtoWithToken(bot)).build()
      case Left(error) =>
        val status = error match
          case AccountError.BotLimitExceeded => Response.Status.BAD_REQUEST
          case _                             => Response.Status.INTERNAL_SERVER_ERROR
        Response.status(status).entity(ErrorDto(error.message)).build()

  @GET
  @Path("/bots")
  @RolesAllowed(Array("**"))
  def listBotAccounts(): Response =
    val ownerId = UUID.fromString(jwt.getSubject)
    val bots    = accountService.getBotAccounts(ownerId)
    Response.ok(bots.map(toBotDto)).build()

  @PUT
  @Path("/bots/{botId}")
  @RolesAllowed(Array("**"))
  def updateBotName(@PathParam("botId") botId: String, req: UpdateBotNameRequest): Response =
    val ownerId = UUID.fromString(jwt.getSubject)
    accountService.updateBotName(UUID.fromString(botId), ownerId, req.name) match
      case Right(bot) => Response.ok(toBotDto(bot)).build()
      case Left(AccountError.NotAuthorized) =>
        Response.status(Response.Status.FORBIDDEN).entity(ErrorDto(AccountError.NotAuthorized.message)).build()
      case Left(error) =>
        Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(error.message)).build()

  @POST
  @Path("/bots/{botId}/rotate-token")
  @RolesAllowed(Array("**"))
  def rotateBotToken(@PathParam("botId") botId: String): Response =
    val ownerId = UUID.fromString(jwt.getSubject)
    accountService.rotateBotToken(UUID.fromString(botId), ownerId) match
      case Right(bot) => Response.ok(RotatedTokenDto(bot.token)).build()
      case Left(AccountError.NotAuthorized) =>
        Response.status(Response.Status.FORBIDDEN).entity(ErrorDto(AccountError.NotAuthorized.message)).build()
      case Left(error) =>
        Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(error.message)).build()

  @DELETE
  @Path("/bots/{botId}")
  @RolesAllowed(Array("**"))
  def deleteBotAccount(@PathParam("botId") botId: String): Response =
    val ownerId = UUID.fromString(jwt.getSubject)
    val botUuid = UUID.fromString(botId)
    accountService.getBotAccountWithOwnerCheck(botUuid, ownerId) match
      case None => Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(AccountError.BotNotFound.message)).build()
      case Some(None) =>
        Response.status(Response.Status.FORBIDDEN).entity(ErrorDto(AccountError.NotAuthorized.message)).build()
      case Some(Some(_)) =>
        accountService.deleteBotAccount(botUuid) match
          case Right(_) => Response.noContent().build()
          case Left(error) =>
            Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(error.message)).build()

  private def toPublicDto(account: UserAccount): PublicAccountDto =
    PublicAccountDto(
      id = account.id.toString,
      username = account.username,
      rating = account.rating,
      createdAt = account.createdAt.toString,
    )

  private def toBotDto(bot: BotAccount): BotAccountDto =
    BotAccountDto(
      id = bot.id.toString,
      name = bot.name,
      rating = bot.rating,
      createdAt = bot.createdAt.toString,
    )

  private def toBotDtoWithToken(bot: BotAccount): BotAccountWithTokenDto =
    BotAccountWithTokenDto(
      id = bot.id.toString,
      name = bot.name,
      rating = bot.rating,
      token = bot.token,
      createdAt = bot.createdAt.toString,
    )

  @GET
  @Path("/official-bots")
  def getOfficialBots: Response =
    val bots = accountService.getOfficialBotAccounts()
    Response.ok(bots.map(toOfficialBotDto)).build()

  @POST
  @Path("/official-bots")
  @RolesAllowed(Array("Admin"))
  def createOfficialBot(req: CreateBotAccountRequest): Response =
    accountService.createOfficialBotAccount(req.name) match
      case Right(bot) =>
        Response.status(Response.Status.CREATED).entity(toOfficialBotDto(bot)).build()
      case Left(error) =>
        Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ErrorDto(error.message)).build()

  @DELETE
  @Path("/official-bots/{botId}")
  @RolesAllowed(Array("Admin"))
  def deleteOfficialBot(@PathParam("botId") botId: String): Response =
    accountService.deleteOfficialBotAccount(UUID.fromString(botId)) match
      case Right(_) => Response.noContent().build()
      case Left(error) =>
        Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(error.message)).build()

  private def toOfficialBotDto(bot: OfficialBotAccount): OfficialBotAccountDto =
    OfficialBotAccountDto(
      id = bot.id.toString,
      name = bot.name,
      rating = bot.rating,
      createdAt = bot.createdAt.toString,
    )
