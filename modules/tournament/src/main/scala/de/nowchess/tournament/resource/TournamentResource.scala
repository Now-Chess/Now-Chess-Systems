package de.nowchess.tournament.resource

import de.nowchess.tournament.dto.*
import de.nowchess.tournament.error.TournamentError
import de.nowchess.tournament.service.{TournamentService, TournamentStreamManager}
import io.smallrye.mutiny.Multi
import jakarta.annotation.security.{PermitAll, RolesAllowed}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{Context, HttpHeaders, MediaType, Response}
import org.eclipse.microprofile.jwt.JsonWebToken
import org.jboss.logging.Logger
import scala.compiletime.uninitialized

@Path("/api/tournament")
@ApplicationScoped
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class TournamentResource:

  private val log = Logger.getLogger(classOf[TournamentResource])

  // scalafix:off DisableSyntax.var
  @Inject var tournamentService: TournamentService   = uninitialized
  @Inject var streamManager: TournamentStreamManager = uninitialized
  @Inject var jwt: JsonWebToken                      = uninitialized
  // scalafix:on

  @GET
  @PermitAll
  def list(): Response =
    val (created, started, finished) = tournamentService.list()
    val dto = TournamentListDto(
      created = created.map(t => tournamentService.toDto(t)),
      started = started.map(t => tournamentService.toDto(t)),
      finished = finished.map(t => tournamentService.toDto(t)),
    )
    Response.ok(dto).build()

  @POST
  @RolesAllowed(Array("**"))
  @Consumes(Array(MediaType.APPLICATION_FORM_URLENCODED))
  def create(
      @FormParam("name") name: String,
      @FormParam("nbRounds") nbRounds: Int,
      @FormParam("clockLimit") clockLimit: Int,
      @FormParam("clockIncrement") clockIncrement: Int,
      @FormParam("rated") @DefaultValue("true") rated: Boolean,
  ): Response =
    val userId = Option(jwt.getSubject).getOrElse("")
    val form   = CreateTournamentForm(name, nbRounds, clockLimit, clockIncrement, rated)
    val t      = tournamentService.create(userId, form)
    Response.status(Response.Status.CREATED).entity(tournamentService.toDto(t)).build()

  @GET
  @Path("/{id}")
  @PermitAll
  def get(@PathParam("id") id: String): Response =
    tournamentService.get(id) match
      case None => Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(s"Tournament $id not found")).build()
      case Some(t) =>
        val standings = tournamentService.getStandings(id)
        Response.ok(tournamentService.toDto(t, standings)).build()

  @DELETE
  @Path("/{id}")
  @RolesAllowed(Array("**"))
  def terminate(@PathParam("id") id: String): Response =
    val userId = Option(jwt.getSubject).getOrElse("")
    tournamentService.terminate(id, userId) match
      case Right(_)    => Response.noContent().build()
      case Left(error) => errorResponse(error)

  @POST
  @Path("/{id}/start")
  @RolesAllowed(Array("**"))
  def start(@PathParam("id") id: String): Response =
    val userId = Option(jwt.getSubject).getOrElse("")
    tournamentService.start(id, userId) match
      case Right(t)    => Response.ok(tournamentService.toDto(t)).build()
      case Left(error) => errorResponse(error)

  @POST
  @Path("/{id}/join")
  @RolesAllowed(Array("**"))
  def join(@PathParam("id") id: String): Response =
    val tokenType = Option(jwt.getClaim[AnyRef]("type")).map(_.toString).getOrElse("")
    if tokenType != "bot" then
      Response.status(Response.Status.FORBIDDEN).entity(ErrorDto("Only bots can join tournaments")).build()
    else
      val botId   = Option(jwt.getSubject).getOrElse("")
      val botName = Option(jwt.getClaim[AnyRef]("name")).map(_.toString).getOrElse(botId)
      tournamentService.join(id, botId, botName) match
        case Right(_)    => Response.ok(OkDto()).build()
        case Left(error) => errorResponse(error)

  @POST
  @Path("/{id}/withdraw")
  @RolesAllowed(Array("**"))
  def withdraw(@PathParam("id") id: String): Response =
    val tokenType = Option(jwt.getClaim[AnyRef]("type")).map(_.toString).getOrElse("")
    if tokenType != "bot" then
      Response.status(Response.Status.FORBIDDEN).entity(ErrorDto("Only bots can withdraw")).build()
    else
      val botId = Option(jwt.getSubject).getOrElse("")
      tournamentService.withdraw(id, botId) match
        case Right(_)    => Response.ok(OkDto()).build()
        case Left(error) => errorResponse(error)

  @GET
  @Path("/{id}/results")
  @Produces(Array("application/x-ndjson"))
  @PermitAll
  def results(
      @PathParam("id") id: String,
      @QueryParam("nb") @DefaultValue("100") nb: Int,
  ): Response =
    tournamentService.get(id) match
      case None => Response.status(Response.Status.NOT_FOUND).entity("").build()
      case Some(_) =>
        val ndjson = tournamentService
          .getResults(id)
          .take(nb)
          .map { r =>
            s"""{"rank":${r.rank},"points":${r.points},"tieBreak":${r.tieBreak},"bot":{"id":"${r.bot.id}","name":"${r.bot.name}"},"nbGames":${r.nbGames},"wins":${r.wins},"draws":${r.draws},"losses":${r.losses}}"""
          }
          .mkString("\n")
        Response.ok(ndjson).`type`("application/x-ndjson").build()

  @GET
  @Path("/{id}/round/{round}")
  @PermitAll
  def roundPairings(@PathParam("id") id: String, @PathParam("round") round: Int): Response =
    tournamentService.get(id) match
      case None => Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(s"Tournament $id not found")).build()
      case Some(_) =>
        val pairings = tournamentService.getPairings(id, round)
        Response.ok(RoundPairingsDto(round, pairings)).build()

  @GET
  @Path("/{id}/export/games")
  @PermitAll
  @Produces(Array(MediaType.APPLICATION_JSON, MediaType.WILDCARD, "application/x-ndjson", "application/x-chess-pgn"))
  def exportGames(@PathParam("id") id: String, @Context headers: HttpHeaders): Response =
    tournamentService.get(id) match
      case None => Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(s"Tournament $id not found")).build()
      case Some(_) =>
        val acceptHeader = Option(headers.getHeaderString("Accept")).getOrElse("")
        val pairings     = tournamentService.getAllPairings(id)
        if acceptHeader.contains("application/x-ndjson") then
          val ndjson = pairings
            .filter(p => Option(p.whiteId).isDefined && Option(p.gameId).isDefined)
            .map { p =>
              val winner = Option(p.winner).map(w => s""""$w"""").getOrElse("null")
              val moves  = Option(p.moveList).getOrElse("")
              s"""{"id":"${p.gameId}","round":${p.round},"white":{"id":"${p.whiteId}","name":"${p.whiteName}"},"black":{"id":"${p.blackId}","name":"${p.blackName}"},"winner":$winner,"moves":"$moves"}"""
            }
            .mkString("\n")
          Response.ok(ndjson).`type`("application/x-ndjson").build()
        else
          val pgn = pairings.flatMap(p => Option(p.moveList)).mkString("\n\n")
          Response.ok(pgn).`type`("application/x-chess-pgn").build()

  @GET
  @Path("/{id}/stream")
  @RolesAllowed(Array("**"))
  @Produces(Array("application/x-ndjson"))
  def stream(@PathParam("id") id: String): Multi[String] =
    tournamentService.get(id) match
      case None => Multi.createFrom().failure(new NotFoundException(s"Tournament $id not found"))
      case Some(_) =>
        val botId = Option(jwt.getSubject).getOrElse("")
        Multi.createFrom().emitter[String] { emitter =>
          streamManager.register(id, botId, emitter)
          emitter.onTermination(() => streamManager.unregister(id, botId, emitter))
        }

  private def errorResponse(error: TournamentError): Response =
    val status = error match
      case TournamentError.NotFound(_)           => Response.Status.NOT_FOUND
      case TournamentError.NotDirector           => Response.Status.FORBIDDEN
      case TournamentError.NotABot               => Response.Status.FORBIDDEN
      case TournamentError.WrongStatus(_)        => Response.Status.CONFLICT
      case TournamentError.AlreadyJoined         => Response.Status.CONFLICT
      case TournamentError.NotJoined             => Response.Status.CONFLICT
      case TournamentError.NotEnoughParticipants => Response.Status.CONFLICT
    Response.status(status).entity(ErrorDto(error.message)).build()
