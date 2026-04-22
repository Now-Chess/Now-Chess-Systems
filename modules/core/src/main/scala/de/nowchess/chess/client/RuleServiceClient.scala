package de.nowchess.chess.client

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

case class RuleSquareRequest(context: GameContext, square: String)
case class RuleMoveRequest(context: GameContext, move: Move)

@Path("/api/rules")
@RegisterRestClient(configKey = "rule-service")
trait RuleServiceClient:

  @POST
  @Path("/candidate-moves")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def candidateMoves(req: RuleSquareRequest): List[Move]

  @POST
  @Path("/legal-moves")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def legalMoves(req: RuleSquareRequest): List[Move]

  @POST
  @Path("/all-legal-moves")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def allLegalMoves(ctx: GameContext): List[Move]

  @POST
  @Path("/is-check")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def isCheck(ctx: GameContext): Boolean

  @POST
  @Path("/is-checkmate")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def isCheckmate(ctx: GameContext): Boolean

  @POST
  @Path("/is-stalemate")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def isStalemate(ctx: GameContext): Boolean

  @POST
  @Path("/is-insufficient-material")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def isInsufficientMaterial(ctx: GameContext): Boolean

  @POST
  @Path("/is-fifty-move-rule")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def isFiftyMoveRule(ctx: GameContext): Boolean

  @POST
  @Path("/is-threefold-repetition")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def isThreefoldRepetition(ctx: GameContext): Boolean

  @POST
  @Path("/apply-move")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def applyMove(req: RuleMoveRequest): GameContext
