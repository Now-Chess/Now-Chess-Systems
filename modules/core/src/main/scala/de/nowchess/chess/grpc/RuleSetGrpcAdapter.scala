package de.nowchess.chess.grpc

import de.nowchess.api.board.Square
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.api.rules.{PostMoveStatus, RuleSet}
import de.nowchess.core.proto.*
import io.quarkus.grpc.GrpcClient
import jakarta.enterprise.context.ApplicationScoped

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@ApplicationScoped
class RuleSetGrpcAdapter extends RuleSet:

  // scalafix:off DisableSyntax.var
  @GrpcClient("rule-grpc")
  var stub: RuleServiceGrpc.RuleServiceBlockingStub = uninitialized
  // scalafix:on DisableSyntax.var

  def candidateMoves(ctx: GameContext)(sq: Square): List[Move] =
    val req =
      ProtoSquareRequest.newBuilder().setContext(CoreProtoMapper.toProtoGameContext(ctx)).setSquare(sq.toString).build()
    stub.candidateMoves(req).getMovesList.asScala.flatMap(CoreProtoMapper.fromProtoMove).toList

  def legalMoves(ctx: GameContext)(sq: Square): List[Move] =
    val req =
      ProtoSquareRequest.newBuilder().setContext(CoreProtoMapper.toProtoGameContext(ctx)).setSquare(sq.toString).build()
    stub.legalMoves(req).getMovesList.asScala.flatMap(CoreProtoMapper.fromProtoMove).toList

  def allLegalMoves(ctx: GameContext): List[Move] =
    stub
      .allLegalMoves(CoreProtoMapper.toProtoGameContext(ctx))
      .getMovesList
      .asScala
      .flatMap(CoreProtoMapper.fromProtoMove)
      .toList

  def isCheck(ctx: GameContext): Boolean =
    stub.isCheck(CoreProtoMapper.toProtoGameContext(ctx)).getValue

  def isCheckmate(ctx: GameContext): Boolean =
    stub.isCheckmate(CoreProtoMapper.toProtoGameContext(ctx)).getValue

  def isStalemate(ctx: GameContext): Boolean =
    stub.isStalemate(CoreProtoMapper.toProtoGameContext(ctx)).getValue

  def isInsufficientMaterial(ctx: GameContext): Boolean =
    stub.isInsufficientMaterial(CoreProtoMapper.toProtoGameContext(ctx)).getValue

  def isFiftyMoveRule(ctx: GameContext): Boolean =
    stub.isFiftyMoveRule(CoreProtoMapper.toProtoGameContext(ctx)).getValue

  def isThreefoldRepetition(ctx: GameContext): Boolean =
    stub.isThreefoldRepetition(CoreProtoMapper.toProtoGameContext(ctx)).getValue

  def applyMove(ctx: GameContext)(move: Move): GameContext =
    val req = ProtoMoveRequest
      .newBuilder()
      .setContext(CoreProtoMapper.toProtoGameContext(ctx))
      .setMove(CoreProtoMapper.toProtoMove(move))
      .build()
    CoreProtoMapper.fromProtoGameContext(stub.applyMove(req))

  override def postMoveStatus(ctx: GameContext): PostMoveStatus =
    val p = stub.postMoveStatus(CoreProtoMapper.toProtoGameContext(ctx))
    PostMoveStatus(
      p.getIsCheckmate,
      p.getIsStalemate,
      p.getIsInsufficientMaterial,
      p.getIsCheck,
      p.getIsThreefoldRepetition,
    )
