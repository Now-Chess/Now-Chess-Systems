package de.nowchess.chess.adapter

import de.nowchess.api.board.Square
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.chess.client.{RuleMoveRequest, RuleServiceClient, RuleSquareRequest}
import de.nowchess.api.rules.{PostMoveStatus, RuleSet}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.rest.client.inject.RestClient

import scala.compiletime.uninitialized

@ApplicationScoped
class RuleSetRestAdapter extends RuleSet:

  // scalafix:off DisableSyntax.var
  @Inject
  @RestClient
  var client: RuleServiceClient = uninitialized
  // scalafix:on DisableSyntax.var

  def candidateMoves(ctx: GameContext)(sq: Square): List[Move] =
    client.candidateMoves(RuleSquareRequest(ctx, sq.toString))

  def legalMoves(ctx: GameContext)(sq: Square): List[Move] =
    client.legalMoves(RuleSquareRequest(ctx, sq.toString))

  def allLegalMoves(ctx: GameContext): List[Move] =
    client.allLegalMoves(ctx)

  def isCheck(ctx: GameContext): Boolean =
    client.isCheck(ctx)

  def isCheckmate(ctx: GameContext): Boolean =
    client.isCheckmate(ctx)

  def isStalemate(ctx: GameContext): Boolean =
    client.isStalemate(ctx)

  def isInsufficientMaterial(ctx: GameContext): Boolean =
    client.isInsufficientMaterial(ctx)

  def isFiftyMoveRule(ctx: GameContext): Boolean =
    client.isFiftyMoveRule(ctx)

  def isThreefoldRepetition(ctx: GameContext): Boolean =
    client.isThreefoldRepetition(ctx)

  def applyMove(ctx: GameContext)(move: Move): GameContext =
    client.applyMove(RuleMoveRequest(ctx, move))

  override def postMoveStatus(ctx: GameContext): PostMoveStatus =
    client.postMoveStatus(ctx)
