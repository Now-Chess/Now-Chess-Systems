package de.nowchess.chess.resource

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.board.{Color, Square}
import de.nowchess.api.dto.*
import de.nowchess.api.game.{
  CorrespondenceClockState,
  DrawReason,
  GameContext,
  GameMode,
  GameResult,
  LiveClockState,
  TimeControl,
  WinReason,
}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.api.player.{PlayerId, PlayerInfo}
import java.time.Instant
import de.nowchess.api.rules.RuleSet
import de.nowchess.chess.controller.Parser
import de.nowchess.chess.engine.GameEngine
import de.nowchess.chess.exception.{BadRequestException, GameNotFoundException}
import de.nowchess.chess.grpc.{IoGrpcClientWrapper, RuleSetGrpcAdapter}
import de.nowchess.chess.observer.*
import de.nowchess.chess.redis.GameRedisSubscriberManager
import de.nowchess.chess.registry.{GameEntry, GameRegistry}
import de.nowchess.security.InternalOnly
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}
import org.eclipse.microprofile.jwt.JsonWebToken
import org.jboss.logging.Logger

import java.util.concurrent.atomic.AtomicReference
import scala.compiletime.uninitialized

@Path("/api/board/game")
@ApplicationScoped
class GameResource:

  private val log = Logger.getLogger(classOf[GameResource])

  // scalafix:off DisableSyntax.var
  @Inject
  var registry: GameRegistry = uninitialized

  @Inject
  var objectMapper: ObjectMapper = uninitialized

  @Inject
  var ioClient: IoGrpcClientWrapper = uninitialized

  @Inject
  var ruleSetAdapter: RuleSetGrpcAdapter = uninitialized

  @Inject
  var jwt: JsonWebToken = uninitialized

  @Inject
  var subscriberManager: GameRedisSubscriberManager = uninitialized
  // scalafix:on DisableSyntax.var

  private val DefaultWhite = PlayerInfo(PlayerId("p1"), "Player 1")
  private val DefaultBlack = PlayerInfo(PlayerId("p2"), "Player 2")

  // ── auth helpers ─────────────────────────────────────────────────────────
  // scalafix:off DisableSyntax.throw

  private def colorOf(entry: GameEntry): Color =
    entry.mode match
      case GameMode.Open => entry.engine.context.turn
      case GameMode.Authenticated =>
        val subject = Option(jwt)
          .flatMap(j => Option(j.getSubject))
          .getOrElse(throw ForbiddenException("Authentication required"))
        if entry.white.id.value == subject then Color.White
        else if entry.black.id.value == subject then Color.Black
        else throw ForbiddenException("You are not a player in this game")

  private def assertIsCurrentPlayer(entry: GameEntry): Unit =
    if entry.mode == GameMode.Authenticated then
      val color = colorOf(entry)
      if color != entry.engine.context.turn then throw ForbiddenException("Not your turn")

  private def assertIsNotBot(): Unit =
    val botType = Option(jwt.getClaim[AnyRef]("type")).map(_.toString).getOrElse("")
    if Set("bot", "official-bot").contains(botType) then throw ForbiddenException("Only bots can make moves")

  // scalafix:on DisableSyntax.throw

  // ── mapping ──────────────────────────────────────────────────────────────

  private def toLegalMoveDto(move: Move): LegalMoveDto =
    val (moveTypeStr, promotionStr) = move.moveType match
      case MoveType.Normal(false)                    => ("normal", None)
      case MoveType.Normal(true)                     => ("capture", None)
      case MoveType.CastleKingside                   => ("castleKingside", None)
      case MoveType.CastleQueenside                  => ("castleQueenside", None)
      case MoveType.EnPassant                        => ("enPassant", None)
      case MoveType.Promotion(PromotionPiece.Queen)  => ("promotion", Some("queen"))
      case MoveType.Promotion(PromotionPiece.Rook)   => ("promotion", Some("rook"))
      case MoveType.Promotion(PromotionPiece.Bishop) => ("promotion", Some("bishop"))
      case MoveType.Promotion(PromotionPiece.Knight) => ("promotion", Some("knight"))
    LegalMoveDto(move.from.toString, move.to.toString, GameDtoMapper.moveToUci(move), moveTypeStr, promotionStr)

  private def playerInfoFrom(dto: Option[PlayerInfoDto], default: PlayerInfo): PlayerInfo =
    dto.fold(default)(d => PlayerInfo(PlayerId(d.id), d.displayName))

  private def toTimeControl(dto: Option[TimeControlDto]): TimeControl =
    dto match
      case None => TimeControl.Unlimited
      case Some(tc) =>
        tc.daysPerMove match
          case Some(d) => TimeControl.Correspondence(d)
          case None =>
            tc.limitSeconds.fold(TimeControl.Unlimited)(l => TimeControl.Clock(l, tc.incrementSeconds.getOrElse(0)))

  private def newEntry(
      ctx: GameContext,
      white: PlayerInfo,
      black: PlayerInfo,
      tc: TimeControl = TimeControl.Unlimited,
      mode: GameMode = GameMode.Open,
  ): GameEntry =
    GameEntry(
      registry.generateId(),
      GameEngine(initialContext = ctx, ruleSet = ruleSetAdapter, timeControl = tc),
      white,
      black,
      mode = mode,
    )

  private def applyMoveInput(engine: GameEngine, uci: String): Option[String] =
    val error = new AtomicReference[Option[String]](None)
    val obs = new Observer:
      def onGameEvent(e: GameEvent): Unit = e match
        case InvalidMoveEvent(_, reason) => error.set(Some(reason.toString))
        case _                           => ()
    engine.subscribe(obs)
    engine.processUserInput(uci)
    engine.unsubscribe(obs)
    error.get()

  // ── response helpers ─────────────────────────────────────────────────────

  private def ok(body: AnyRef): Response      = Response.ok(body).build()
  private def created(body: AnyRef): Response = Response.status(Response.Status.CREATED).entity(body).build()

  // scalafix:off DisableSyntax.throw
  private def assertGameNotOver(entry: GameEntry): Unit =
    if entry.engine.context.result.isDefined then throw BadRequestException("GAME_OVER", "Game is already over")
  // scalafix:on DisableSyntax.throw

  // ── endpoints ────────────────────────────────────────────────────────────
  // scalafix:off DisableSyntax.throw

  @POST
  @InternalOnly
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def createGame(body: CreateGameRequestDto): Response =
    val req   = Option(body).getOrElse(CreateGameRequestDto(None, None, None, None))
    val white = playerInfoFrom(req.white, DefaultWhite)
    val black = playerInfoFrom(req.black, DefaultBlack)
    val tc    = toTimeControl(req.timeControl)
    val mode  = req.mode.getOrElse(GameMode.Open)
    val entry = newEntry(GameContext.initial, white, black, tc, mode)
    registry.store(entry)
    subscriberManager.subscribeGame(entry.gameId)
    log.infof(
      "Game %s created — white=%s black=%s mode=%s",
      entry.gameId,
      white.displayName,
      black.displayName,
      mode.toString,
    )
    created(GameDtoMapper.toGameFullDto(entry, ioClient))

  @GET
  @Path("/{gameId}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def getGame(@PathParam("gameId") gameId: String): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    ok(GameDtoMapper.toGameFullDto(entry, ioClient))

  @POST
  @Path("/{gameId}/resign")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def resignGame(@PathParam("gameId") gameId: String): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    assertGameNotOver(entry)
    val color = colorOf(entry)
    log.infof("Game %s — resign by %s", gameId, color.label)
    entry.engine.resign(color)
    registry.update(entry.copy(resigned = true))
    ok(OkResponseDto())

  @POST
  @Path("/{gameId}/move/{uci}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def makeMove(@PathParam("gameId") gameId: String, @PathParam("uci") uci: String): Response =
    assertIsNotBot()
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    assertGameNotOver(entry)
    assertIsCurrentPlayer(entry)
    log.debugf("Game %s — move %s by %s", gameId, uci, colorOf(entry).label)
    if Parser.parseMove(uci).isEmpty then
      throw BadRequestException("INVALID_UCI", s"Invalid UCI notation: $uci", Some("uci"))
    applyMoveInput(entry.engine, uci).foreach(err => throw BadRequestException("INVALID_MOVE", err, Some("uci")))
    registry.update(entry)
    ok(GameDtoMapper.toGameStateDto(entry, ioClient))

  @GET
  @Path("/{gameId}/moves")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def getLegalMoves(
      @PathParam("gameId") gameId: String,
      @QueryParam("square") square: String,
  ): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    val ctx   = entry.engine.context
    val moves =
      if Option(square).isEmpty || square.isEmpty then entry.engine.ruleSet.allLegalMoves(ctx)
      else
        val sq = Square
          .fromAlgebraic(square)
          .getOrElse(throw BadRequestException("INVALID_SQUARE", s"Invalid square: $square", Some("square")))
        entry.engine.ruleSet.legalMoves(ctx)(sq)
    ok(LegalMovesResponseDto(moves.map(toLegalMoveDto)))

  @POST
  @Path("/{gameId}/undo")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def undoMove(@PathParam("gameId") gameId: String): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    if !entry.engine.canUndo then throw BadRequestException("NO_UNDO", "No moves to undo")
    entry.engine.undo()
    registry.update(entry)
    ok(GameDtoMapper.toGameStateDto(entry, ioClient))

  @POST
  @Path("/{gameId}/redo")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def redoMove(@PathParam("gameId") gameId: String): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    if !entry.engine.canRedo then throw BadRequestException("NO_REDO", "No moves to redo")
    entry.engine.redo()
    registry.update(entry)
    ok(GameDtoMapper.toGameStateDto(entry, ioClient))

  @POST
  @Path("/{gameId}/draw/{action}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def drawAction(
      @PathParam("gameId") gameId: String,
      @PathParam("action") action: String,
  ): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    assertGameNotOver(entry)
    val color = colorOf(entry)
    action match
      case "offer"   => entry.engine.offerDraw(color); registry.update(entry); ok(OkResponseDto())
      case "accept"  => entry.engine.acceptDraw(color); registry.update(entry); ok(OkResponseDto())
      case "decline" => entry.engine.declineDraw(color); registry.update(entry); ok(OkResponseDto())
      case "claim"   => entry.engine.claimDraw(); registry.update(entry); ok(OkResponseDto())
      case _         => throw BadRequestException("INVALID_ACTION", s"Unknown draw action: $action", Some("action"))

  @POST
  @Path("/{gameId}/takeback/{action}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def takebackAction(
      @PathParam("gameId") gameId: String,
      @PathParam("action") action: String,
  ): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    assertGameNotOver(entry)
    val color = colorOf(entry)
    action match
      case "request" => entry.engine.requestTakeback(color); registry.update(entry); ok(OkResponseDto())
      case "accept" =>
        entry.engine.acceptTakeback(color); registry.update(entry); ok(GameDtoMapper.toGameStateDto(entry, ioClient))
      case "decline" => entry.engine.declineTakeback(color); registry.update(entry); ok(OkResponseDto())
      case _         => throw BadRequestException("INVALID_ACTION", s"Unknown takeback action: $action", Some("action"))

  @POST
  @Path("/import/fen")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def importFen(body: ImportFenRequestDto): Response =
    val ctx   = ioClient.importFen(body.fen)
    val white = playerInfoFrom(body.white, DefaultWhite)
    val black = playerInfoFrom(body.black, DefaultBlack)
    val tc    = toTimeControl(body.timeControl)
    val entry = newEntry(ctx, white, black, tc)
    registry.store(entry)
    subscriberManager.subscribeGame(entry.gameId)
    log.infof("Imported FEN game %s", entry.gameId)
    created(GameDtoMapper.toGameFullDto(entry, ioClient))

  @POST
  @Path("/import/pgn")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def importPgn(body: ImportPgnRequestDto): Response =
    val ctx   = ioClient.importPgn(body.pgn)
    val entry = newEntry(ctx, DefaultWhite, DefaultBlack)
    registry.store(entry)
    subscriberManager.subscribeGame(entry.gameId)
    log.infof("Imported PGN game %s", entry.gameId)
    created(GameDtoMapper.toGameFullDto(entry, ioClient))

  @GET
  @Path("/{gameId}/export/fen")
  @Produces(Array(MediaType.TEXT_PLAIN))
  def exportFen(@PathParam("gameId") gameId: String): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    ok(ioClient.exportFen(entry.engine.context))

  @GET
  @Path("/{gameId}/export/pgn")
  @Produces(Array("application/x-chess-pgn"))
  def exportPgn(@PathParam("gameId") gameId: String): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    ok(ioClient.exportPgn(entry.engine.context))
  // scalafix:on DisableSyntax.throw
