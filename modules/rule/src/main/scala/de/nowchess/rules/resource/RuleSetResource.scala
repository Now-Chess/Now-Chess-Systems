package de.nowchess.rules.resource

import de.nowchess.api.board.Square
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.rules.dto.*
import de.nowchess.rules.sets.DefaultRules
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType

@Path("/api/rules")
@ApplicationScoped
class RuleSetResource:
  private val rules = DefaultRules

  // scalafix:off DisableSyntax.throw
  private def parseSquare(s: String): Square =
    Square.fromAlgebraic(s).getOrElse(throw new BadRequestException(s"Invalid square: $s"))
  // scalafix:on DisableSyntax.throw

  @POST
  @Path("/candidate-moves")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def candidateMoves(req: ContextSquareRequest): List[Move] =
    rules.candidateMoves(req.context)(parseSquare(req.square))

  @POST
  @Path("/legal-moves")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def legalMoves(req: ContextSquareRequest): List[Move] =
    rules.legalMoves(req.context)(parseSquare(req.square))

  @POST
  @Path("/all-legal-moves")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def allLegalMoves(ctx: GameContext): List[Move] =
    rules.allLegalMoves(ctx)

  @POST
  @Path("/is-check")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def isCheck(ctx: GameContext): Boolean =
    rules.isCheck(ctx)

  @POST
  @Path("/is-checkmate")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def isCheckmate(ctx: GameContext): Boolean =
    rules.isCheckmate(ctx)

  @POST
  @Path("/is-stalemate")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def isStalemate(ctx: GameContext): Boolean =
    rules.isStalemate(ctx)

  @POST
  @Path("/is-insufficient-material")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def isInsufficientMaterial(ctx: GameContext): Boolean =
    rules.isInsufficientMaterial(ctx)

  @POST
  @Path("/is-fifty-move-rule")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def isFiftyMoveRule(ctx: GameContext): Boolean =
    rules.isFiftyMoveRule(ctx)

  @POST
  @Path("/is-threefold-repetition")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def isThreefoldRepetition(ctx: GameContext): Boolean =
    rules.isThreefoldRepetition(ctx)

  @POST
  @Path("/apply-move")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def applyMove(req: ContextMoveRequest): GameContext =
    rules.applyMove(req.context)(req.move)
