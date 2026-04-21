package de.nowchess.chess.resource

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.board.Square
import de.nowchess.api.dto.*
import de.nowchess.api.game.{DrawReason, GameContext, GameResult}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.api.player.{PlayerId, PlayerInfo}
import de.nowchess.chess.controller.Parser
import de.nowchess.chess.engine.GameEngine
import de.nowchess.chess.exception.{BadRequestException, GameNotFoundException}
import de.nowchess.chess.observer.*
import de.nowchess.chess.registry.{GameEntry, GameRegistry}
import de.nowchess.io.fen.{FenExporter, FenParser}
import de.nowchess.io.pgn.{PgnExporter, PgnParser}
import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.{MediaType, Response}

import java.util.concurrent.atomic.AtomicReference
import scala.compiletime.uninitialized

@Path("/api/board/game")
@ApplicationScoped
class GameResource:

  // scalafix:off DisableSyntax.var
  @Inject
  var registry: GameRegistry = uninitialized

  @Inject
  var objectMapper: ObjectMapper = uninitialized
  // scalafix:on DisableSyntax.var

  private val DefaultWhite = PlayerInfo(PlayerId("p1"), "Player 1")
  private val DefaultBlack = PlayerInfo(PlayerId("p2"), "Player 2")

  // ── mapping ──────────────────────────────────────────────────────────────

  private def statusOf(entry: GameEntry): String =
    if entry.engine.pendingDrawOfferBy.isDefined then "drawOffered"
    else
      val ctx = entry.engine.context
      ctx.result match
        case Some(GameResult.Win(_)) =>
          if entry.resigned then "resign" else "checkmate"
        case Some(GameResult.Draw(DrawReason.Stalemate))            => "stalemate"
        case Some(GameResult.Draw(DrawReason.InsufficientMaterial)) => "insufficientMaterial"
        case Some(GameResult.Draw(_))                               => "draw"
        case None =>
          if ctx.halfMoveClock >= 100 then "fiftyMoveAvailable"
          else if entry.engine.ruleSet.isCheck(ctx) then "check"
          else "started"

  private def moveToUci(move: Move): String =
    val base = s"${move.from}${move.to}"
    move.moveType match
      case MoveType.Promotion(PromotionPiece.Queen)  => s"${base}q"
      case MoveType.Promotion(PromotionPiece.Rook)   => s"${base}r"
      case MoveType.Promotion(PromotionPiece.Bishop) => s"${base}b"
      case MoveType.Promotion(PromotionPiece.Knight) => s"${base}n"
      case _                                         => base

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
    LegalMoveDto(move.from.toString, move.to.toString, moveToUci(move), moveTypeStr, promotionStr)

  private def toPlayerDto(info: PlayerInfo): PlayerInfoDto =
    PlayerInfoDto(info.id.value, info.displayName)

  private def toGameStateDto(entry: GameEntry): GameStateDto =
    val ctx = entry.engine.context
    GameStateDto(
      fen = FenExporter.exportGameContext(ctx),
      pgn = PgnExporter.exportGame(
        Map(
          "Event"  -> "NowChess game",
          "White"  -> entry.white.displayName,
          "Black"  -> entry.black.displayName,
          "Result" -> "*",
        ),
        ctx.moves,
      ),
      turn = ctx.turn.label.toLowerCase,
      status = statusOf(entry),
      winner = ctx.result.collect { case GameResult.Win(c) => c.label.toLowerCase },
      moves = ctx.moves.map(moveToUci),
      undoAvailable = entry.engine.canUndo,
      redoAvailable = entry.engine.canRedo,
    )

  private def toGameFullDto(entry: GameEntry): GameFullDto =
    GameFullDto(entry.gameId, toPlayerDto(entry.white), toPlayerDto(entry.black), toGameStateDto(entry))

  private def playerInfoFrom(dto: Option[PlayerInfoDto], default: PlayerInfo): PlayerInfo =
    dto.fold(default)(d => PlayerInfo(PlayerId(d.id), d.displayName))

  private def newEntry(ctx: GameContext, white: PlayerInfo, black: PlayerInfo): GameEntry =
    GameEntry(registry.generateId(), GameEngine(initialContext = ctx), white, black)

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
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def createGame(body: CreateGameRequestDto): Response =
    val req   = Option(body).getOrElse(CreateGameRequestDto(None, None))
    val white = playerInfoFrom(req.white, DefaultWhite)
    val black = playerInfoFrom(req.black, DefaultBlack)
    val entry = newEntry(GameContext.initial, white, black)
    registry.store(entry)
    println(s"Created game ${entry.gameId}")
    created(toGameFullDto(entry))

  @GET
  @Path("/{gameId}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def getGame(@PathParam("gameId") gameId: String): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    ok(toGameFullDto(entry))

  @GET
  @Path("/{gameId}/stream")
  @Produces(Array("application/x-ndjson"))
  def streamGame(@PathParam("gameId") gameId: String): Multi[String] =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    Multi
      .createFrom()
      .emitter[String] { emitter =>
        emitter.emit(objectMapper.writeValueAsString(GameFullEventDto(toGameFullDto(entry))) + "\n")
        val obs = new Observer:
          def onGameEvent(event: GameEvent): Unit =
            registry.get(gameId).foreach { updated =>
              emitter.emit(
                objectMapper.writeValueAsString(GameStateEventDto(toGameStateDto(updated))) + "\n",
              )
            }
        entry.engine.subscribe(obs)
        emitter.onTermination(() => entry.engine.unsubscribe(obs))
      }

  @POST
  @Path("/{gameId}/resign")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def resignGame(@PathParam("gameId") gameId: String): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    assertGameNotOver(entry)
    entry.engine.resign()
    registry.update(entry.copy(resigned = true))
    ok(OkResponseDto())

  @POST
  @Path("/{gameId}/move/{uci}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def makeMove(@PathParam("gameId") gameId: String, @PathParam("uci") uci: String): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    assertGameNotOver(entry)
    val (from, to, promoOpt) = Parser
      .parseMove(uci)
      .getOrElse(throw BadRequestException("INVALID_UCI", s"Invalid UCI notation: $uci", Some("uci")))
    val candidates  = entry.engine.ruleSet.legalMoves(entry.engine.context)(from).filter(_.to == to)
    val isPromotion = candidates.exists { case Move(_, _, MoveType.Promotion(_)) => true; case _ => false }
    if candidates.isEmpty || (isPromotion && promoOpt.isEmpty) then
      throw BadRequestException("INVALID_MOVE", s"$uci is not a legal move", Some("uci"))
    applyMoveInput(entry.engine, uci).foreach(err => throw BadRequestException("INVALID_MOVE", err, Some("uci")))
    ok(toGameStateDto(entry))

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
    ok(toGameStateDto(entry))

  @POST
  @Path("/{gameId}/redo")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def redoMove(@PathParam("gameId") gameId: String): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    if !entry.engine.canRedo then throw BadRequestException("NO_REDO", "No moves to redo")
    entry.engine.redo()
    ok(toGameStateDto(entry))

  @POST
  @Path("/{gameId}/draw/{action}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def drawAction(
      @PathParam("gameId") gameId: String,
      @PathParam("action") action: String,
  ): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    assertGameNotOver(entry)
    action match
      case "offer" =>
        entry.engine.offerDraw(entry.engine.context.turn)
        ok(OkResponseDto())
      case "accept" =>
        entry.engine.acceptDraw(entry.engine.context.turn)
        ok(OkResponseDto())
      case "decline" =>
        entry.engine.declineDraw(entry.engine.context.turn)
        ok(OkResponseDto())
      case "claim" =>
        entry.engine.claimDraw()
        ok(OkResponseDto())
      case _ =>
        throw BadRequestException("INVALID_ACTION", s"Unknown draw action: $action", Some("action"))

  @POST
  @Path("/import/fen")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def importFen(body: ImportFenRequestDto): Response =
    val ctx = FenParser.parseFen(body.fen) match
      case Left(err)  => throw BadRequestException("INVALID_FEN", err, Some("fen"))
      case Right(ctx) => ctx
    val white = playerInfoFrom(body.white, DefaultWhite)
    val black = playerInfoFrom(body.black, DefaultBlack)
    val entry = newEntry(ctx, white, black)
    registry.store(entry)
    created(toGameFullDto(entry))

  @POST
  @Path("/import/pgn")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def importPgn(body: ImportPgnRequestDto): Response =
    val engine = GameEngine()
    engine.loadGame(PgnParser, body.pgn) match
      case Left(err) => throw BadRequestException("INVALID_PGN", err, Some("pgn"))
      case Right(_)  => ()
    val entry = GameEntry(registry.generateId(), engine, DefaultWhite, DefaultBlack)
    registry.store(entry)
    created(toGameFullDto(entry))

  @GET
  @Path("/{gameId}/export/fen")
  @Produces(Array(MediaType.TEXT_PLAIN))
  def exportFen(@PathParam("gameId") gameId: String): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    ok(FenExporter.exportGameContext(entry.engine.context))

  @GET
  @Path("/{gameId}/export/pgn")
  @Produces(Array("application/x-chess-pgn"))
  def exportPgn(@PathParam("gameId") gameId: String): Response =
    val entry = registry.get(gameId).getOrElse(throw GameNotFoundException(gameId))
    val pgn = PgnExporter.exportGame(
      Map(
        "Event"  -> "NowChess game",
        "White"  -> entry.white.displayName,
        "Black"  -> entry.black.displayName,
        "Result" -> "*",
      ),
      entry.engine.context.moves,
    )
    ok(pgn)
  // scalafix:on DisableSyntax.throw
