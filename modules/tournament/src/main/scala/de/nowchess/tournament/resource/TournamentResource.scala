package de.nowchess.tournament.resource

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import de.nowchess.tournament.dto.*
import de.nowchess.tournament.error.TournamentError
import de.nowchess.tournament.service.{
  ExternalTournamentClient,
  TournamentServerRegistry,
  TournamentService,
  TournamentStreamManager,
}
import jakarta.annotation.security.{PermitAll, RolesAllowed}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{Context, HttpHeaders, MediaType, Response, StreamingOutput}
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.jwt.JsonWebToken
import java.util.Optional
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@Path("/api/tournament")
@ApplicationScoped
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class TournamentResource:

  private val log = Logger.getLogger(classOf[TournamentResource])

  // scalafix:off DisableSyntax.var
  @Inject var tournamentService: TournamentService     = uninitialized
  @Inject var streamManager: TournamentStreamManager   = uninitialized
  @Inject var jwt: JsonWebToken                        = uninitialized
  @Inject var registry: TournamentServerRegistry       = uninitialized
  @Inject var externalClient: ExternalTournamentClient = uninitialized
  @Inject var objectMapper: ObjectMapper               = uninitialized
  @Context var headers: HttpHeaders                    = uninitialized

  @ConfigProperty(name = "nowchess.tournament.self-url")
  var selfUrl: Optional[String] = uninitialized
  // scalafix:on

  @GET
  @PermitAll
  def list(): Response =
    val (created, started, finished) = tournamentService.list()
    val internalCreated              = created.map(t => objectMapper.valueToTree[JsonNode](tournamentService.toDto(t)))
    val internalStarted              = started.map(t => objectMapper.valueToTree[JsonNode](tournamentService.toDto(t)))
    val internalFinished             = finished.map(t => objectMapper.valueToTree[JsonNode](tournamentService.toDto(t)))

    val (extCreated, extStarted, extFinished) = registry
      .serverUrls()
      .foldLeft(
        (List.empty[JsonNode], List.empty[JsonNode], List.empty[JsonNode]),
      ) { case ((ac, as, af), url) =>
        externalClient.fetchList(url).fold((ac, as, af)) { node =>
          val c = node.path("created").elements().asScala.toList
          val s = node.path("started").elements().asScala.toList
          val f = node.path("finished").elements().asScala.toList
          (c ++ s ++ f).foreach(t => registry.bindTournament(t.path("id").asText(), url))
          (ac ++ c, as ++ s, af ++ f)
        }
      }

    val merged      = objectMapper.createObjectNode()
    val createdArr  = objectMapper.createArrayNode()
    val startedArr  = objectMapper.createArrayNode()
    val finishedArr = objectMapper.createArrayNode()
    (internalCreated ++ extCreated).foreach(createdArr.add)
    (internalStarted ++ extStarted).foreach(startedArr.add)
    (internalFinished ++ extFinished).foreach(finishedArr.add)
    merged.set("created", createdArr)
    merged.set("started", startedArr)
    merged.set("finished", finishedArr)
    Response.ok(merged).build()

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
    selfUrl.ifPresent { url =>
      registry.serverUrls().foreach { remoteUrl =>
        if !externalClient.replicateTournament(remoteUrl, toReplicateRequest(t), url) then
          log.warnf("Failed to replicate tournament %s to %s", t.id, remoteUrl)
      }
    }
    Response.status(Response.Status.CREATED).entity(tournamentService.toDto(t)).build()

  @GET
  @Path("/{id}")
  @PermitAll
  def get(@PathParam("id") id: String): Response =
    tournamentService.get(id) match
      case Some(t) =>
        val standings = tournamentService.getStandings(id)
        Response.ok(tournamentService.toDto(t, standings)).build()
      case None =>
        resolveServer(id)
          .flatMap(url => externalClient.fetch(url, id).map(node => Response.ok(node).build()))
          .getOrElse(Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(s"Tournament $id not found")).build())

  @POST
  @Path("/replicate")
  @PermitAll
  def replicate(req: ReplicateTournamentRequest): Response =
    val originUrl = Option(headers.getHeaderString("X-Origin-Url")).getOrElse("")
    if originUrl.isEmpty then
      Response.status(Response.Status.BAD_REQUEST).entity(ErrorDto("Missing X-Origin-Url header")).build()
    else
      tournamentService.get(req.id) match
        case Some(_) => Response.status(Response.Status.CONFLICT).entity(ErrorDto("Tournament already exists")).build()
        case None =>
          tournamentService.replicate(req, originUrl)
          Response.status(Response.Status.CREATED).build()

  @DELETE
  @Path("/{id}")
  @RolesAllowed(Array("**"))
  def terminate(@PathParam("id") id: String): Response =
    val userId = Option(jwt.getSubject).getOrElse("")
    tournamentService.get(id).flatMap(t => Option(t.originServerUrl)) match
      case Some(originUrl) =>
        val auth           = Option(headers.getHeaderString("Authorization"))
        val (status, body) = externalClient.proxyPost(originUrl, s"api/tournament/$id", auth)
        Response.status(status).entity(body).build()
      case None =>
        tournamentService.terminate(id, userId) match
          case Right(_)    => Response.noContent().build()
          case Left(error) => errorResponse(error)

  @POST
  @Path("/{id}/start")
  @RolesAllowed(Array("**"))
  def start(@PathParam("id") id: String): Response =
    val userId = Option(jwt.getSubject).getOrElse("")
    tournamentService.get(id).flatMap(t => Option(t.originServerUrl)) match
      case Some(originUrl) =>
        val auth           = Option(headers.getHeaderString("Authorization"))
        val (status, body) = externalClient.proxyPost(originUrl, s"api/tournament/$id/start", auth)
        Response.status(status).entity(body).build()
      case None =>
        tournamentService.start(id, userId) match
          case Right(t) => Response.ok(tournamentService.toDto(t)).build()
          case Left(error) =>
            error match
              case TournamentError.NotFound(_) =>
                val auth = Option(headers.getHeaderString("Authorization"))
                resolveServer(id)
                  .map { url =>
                    val (status, body) = externalClient.proxyPost(url, s"api/tournament/$id/start", auth)
                    Response.status(status).entity(body).build()
                  }
                  .getOrElse(errorResponse(error))
              case _ => errorResponse(error)

  @POST
  @Path("/{id}/join")
  @RolesAllowed(Array("**"))
  def join(@PathParam("id") id: String): Response =
    val tokenType = Option(jwt.getClaim[AnyRef]("type")).map(_.toString).getOrElse("")
    if tokenType != "bot" then
      Response.status(Response.Status.FORBIDDEN).entity(ErrorDto("Only bots can join tournaments")).build()
    else
      tournamentService.get(id).flatMap(t => Option(t.originServerUrl)) match
        case Some(originUrl) =>
          val auth           = Option(headers.getHeaderString("Authorization"))
          val (status, body) = externalClient.proxyPost(originUrl, s"api/tournament/$id/join", auth)
          Response.status(status).entity(body).build()
        case None =>
          val botId   = Option(jwt.getSubject).getOrElse("")
          val botName = Option(jwt.getClaim[AnyRef]("name")).map(_.toString).getOrElse(botId)
          tournamentService.join(id, botId, botName) match
            case Right(_) => Response.ok(OkDto()).build()
            case Left(error) =>
              error match
                case TournamentError.NotFound(_) =>
                  val auth = Option(headers.getHeaderString("Authorization"))
                  resolveServer(id)
                    .map { url =>
                      val (status, body) = externalClient.proxyPost(url, s"api/tournament/$id/join", auth)
                      Response.status(status).entity(body).build()
                    }
                    .getOrElse(errorResponse(error))
                case _ => errorResponse(error)

  @POST
  @Path("/{id}/withdraw")
  @RolesAllowed(Array("**"))
  def withdraw(@PathParam("id") id: String): Response =
    val tokenType = Option(jwt.getClaim[AnyRef]("type")).map(_.toString).getOrElse("")
    if tokenType != "bot" then
      Response.status(Response.Status.FORBIDDEN).entity(ErrorDto("Only bots can withdraw")).build()
    else
      tournamentService.get(id).flatMap(t => Option(t.originServerUrl)) match
        case Some(originUrl) =>
          val auth           = Option(headers.getHeaderString("Authorization"))
          val (status, body) = externalClient.proxyPost(originUrl, s"api/tournament/$id/withdraw", auth)
          Response.status(status).entity(body).build()
        case None =>
          val botId = Option(jwt.getSubject).getOrElse("")
          tournamentService.withdraw(id, botId) match
            case Right(_) => Response.ok(OkDto()).build()
            case Left(error) =>
              error match
                case TournamentError.NotFound(_) =>
                  val auth = Option(headers.getHeaderString("Authorization"))
                  resolveServer(id)
                    .map { url =>
                      val (status, body) = externalClient.proxyPost(url, s"api/tournament/$id/withdraw", auth)
                      Response.status(status).entity(body).build()
                    }
                    .getOrElse(errorResponse(error))
                case _ => errorResponse(error)

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
      case Some(_) =>
        val pairings = tournamentService.getPairings(id, round)
        Response.ok(RoundPairingsDto(round, pairings)).build()
      case None =>
        resolveServer(id)
          .flatMap(url => externalClient.fetchPairings(url, id, round).map(node => Response.ok(node).build()))
          .getOrElse(Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(s"Tournament $id not found")).build())

  @GET
  @Path("/{id}/export/games")
  @PermitAll
  @Produces(Array(MediaType.APPLICATION_JSON, MediaType.WILDCARD, "application/x-ndjson", "application/x-chess-pgn"))
  def exportGames(@PathParam("id") id: String, @Context reqHeaders: HttpHeaders): Response =
    tournamentService.get(id) match
      case None => Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(s"Tournament $id not found")).build()
      case Some(_) =>
        val acceptHeader = Option(reqHeaders.getHeaderString("Accept")).getOrElse("")
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
  def stream(@PathParam("id") id: String): Response =
    tournamentService.get(id) match
      case Some(t) if Option(t.originServerUrl).isDefined =>
        val auth = Option(headers.getHeaderString("Authorization"))
        externalClient.proxyGetStream(t.originServerUrl, s"api/tournament/$id/stream", auth)
          .map { inputStream =>
            Response
              .ok(new StreamingOutput {
                def write(output: java.io.OutputStream): Unit =
                  val buf = new Array[Byte](4096)
                  // scalafix:off DisableSyntax.var
                  var n = inputStream.read(buf)
                  while n >= 0 do
                    output.write(buf, 0, n)
                    output.flush()
                    n = inputStream.read(buf)
                // scalafix:on
              })
              .`type`("application/x-ndjson")
              .build()
          }
          .getOrElse(Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(s"Tournament $id stream unavailable")).build())
      case Some(_) =>
        val botId = Option(jwt.getSubject).getOrElse("")
        val queue   = new java.util.concurrent.LinkedBlockingQueue[Option[String]]()
        val emitter = new io.smallrye.mutiny.subscription.MultiEmitter[String] {
          def emit(item: String): io.smallrye.mutiny.subscription.MultiEmitter[String] =
            queue.put(Some(item)); this
          def fail(failure: Throwable): Unit = queue.put(None)
          def complete(): Unit               = queue.put(None)
          def requested(): Long              = Long.MaxValue
          def isCancelled: Boolean           = false
          def onTermination(
              onTermination: java.lang.Runnable,
          ): io.smallrye.mutiny.subscription.MultiEmitter[String] = this
        }
        streamManager.register(id, botId, emitter)
        Response
          .ok(new StreamingOutput {
            def write(output: java.io.OutputStream): Unit =
              try
                // scalafix:off DisableSyntax.var
                var cont = true
                while cont do
                  queue.take() match
                    case None       => cont = false
                    case Some(line) =>
                      output.write((line + "\n").getBytes("UTF-8"))
                      output.flush()
                // scalafix:on
              finally streamManager.unregister(id, botId, emitter)
          })
          .`type`("application/x-ndjson")
          .build()
      case None =>
        val auth = Option(headers.getHeaderString("Authorization"))
        resolveServer(id)
          .flatMap(url => externalClient.proxyGetStream(url, s"api/tournament/$id/stream", auth))
          .map { inputStream =>
            Response
              .ok(new StreamingOutput {
                def write(output: java.io.OutputStream): Unit =
                  val buf = new Array[Byte](4096)
                  // scalafix:off DisableSyntax.var
                  var n = inputStream.read(buf)
                  while n >= 0 do
                    output.write(buf, 0, n)
                    output.flush()
                    n = inputStream.read(buf)
                // scalafix:on
              })
              .`type`("application/x-ndjson")
              .build()
          }
          .getOrElse(Response.status(Response.Status.NOT_FOUND).entity(ErrorDto(s"Tournament $id not found")).build())

  @GET
  @Path("/{id}/game/{gameId}")
  @PermitAll
  def getGame(@PathParam("id") id: String, @PathParam("gameId") gameId: String): Response =
    resolveServer(id)
      .flatMap(url => externalClient.fetch(url, s"$id/game/$gameId").map(node => Response.ok(node).build()))
      .getOrElse(Response.status(Response.Status.NOT_FOUND).build())

  @GET
  @Path("/{id}/game/{gameId}/stream")
  @PermitAll
  @Produces(Array("application/x-ndjson"))
  def streamGame(@PathParam("id") id: String, @PathParam("gameId") gameId: String): Response =
    val auth = Option(headers.getHeaderString("Authorization"))
    resolveServer(id)
      .flatMap(url => externalClient.proxyGetStream(url, s"api/tournament/$id/game/$gameId/stream", auth))
      .map { stream =>
        Response
          .ok(new StreamingOutput {
            def write(output: java.io.OutputStream): Unit =
              val buf = new Array[Byte](4096)
              // scalafix:off DisableSyntax.var
              var n = stream.read(buf)
              while n >= 0 do
                output.write(buf, 0, n)
                output.flush()
                n = stream.read(buf)
            // scalafix:on
          })
          .`type`("application/x-ndjson")
          .build()
      }
      .getOrElse(Response.status(Response.Status.NOT_FOUND).build())

  @POST
  @Path("/{id}/game/{gameId}/move/{uci}")
  @RolesAllowed(Array("**"))
  def makeMove(
      @PathParam("id") id: String,
      @PathParam("gameId") gameId: String,
      @PathParam("uci") uci: String,
  ): Response =
    val auth = Option(headers.getHeaderString("Authorization"))
    resolveServer(id)
      .map { url =>
        val (status, body) = externalClient.proxyPost(url, s"api/tournament/$id/game/$gameId/move/$uci", auth)
        Response.status(status).entity(body).build()
      }
      .getOrElse(Response.status(Response.Status.NOT_FOUND).build())

  private def resolveServer(tournamentId: String): Option[String] =
    tournamentService.get(tournamentId)
      .flatMap(t => Option(t.originServerUrl))
      .orElse(registry.findServerUrl(tournamentId))
      .orElse {
        registry
          .serverUrls()
          .find(url => externalClient.fetch(url, tournamentId).isDefined)
          .map { url =>
            registry.bindTournament(tournamentId, url)
            url
          }
      }

  private def toReplicateRequest(t: de.nowchess.tournament.domain.Tournament): ReplicateTournamentRequest =
    ReplicateTournamentRequest(
      id = t.id,
      fullName = t.fullName,
      nbRounds = t.nbRounds,
      clockLimit = t.clockLimit,
      clockIncrement = t.clockIncrement,
      rated = t.rated,
      createdBy = t.createdBy,
      startsAt = Option(t.startsAt).getOrElse(java.time.Instant.now()),
      status = t.status,
    )

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
