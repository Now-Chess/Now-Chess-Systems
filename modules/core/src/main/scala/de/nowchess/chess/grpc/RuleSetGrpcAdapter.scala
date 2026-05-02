package de.nowchess.chess.grpc

import de.nowchess.api.board.Square
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.api.rules.{PostMoveStatus, RuleSet}
import de.nowchess.core.proto.*
import io.quarkus.grpc.GrpcClient
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@ApplicationScoped
class RuleSetGrpcAdapter extends RuleSet:

  private val log = Logger.getLogger(classOf[RuleSetGrpcAdapter])

  // scalafix:off DisableSyntax.var
  @GrpcClient("rule-grpc")
  var stub: RuleServiceGrpc.RuleServiceBlockingStub = uninitialized
  // scalafix:on DisableSyntax.var

  def candidateMoves(ctx: GameContext)(sq: Square): List[Move] =
    try
      val req =
        ProtoSquareRequest
          .newBuilder()
          .setContext(CoreProtoMapper.toProtoGameContext(ctx))
          .setSquare(sq.toString)
          .build()
      stub.candidateMoves(req).getMovesList.asScala.flatMap(CoreProtoMapper.fromProtoMove).toList
    catch
      case ex: Exception =>
        log.warnf(ex, "Rule gRPC candidateMoves failed")
        // scalafix:off DisableSyntax.throw
        throw ex
      // scalafix:on DisableSyntax.throw

  def legalMoves(ctx: GameContext)(sq: Square): List[Move] =
    try
      val req =
        ProtoSquareRequest
          .newBuilder()
          .setContext(CoreProtoMapper.toProtoGameContext(ctx))
          .setSquare(sq.toString)
          .build()
      stub.legalMoves(req).getMovesList.asScala.flatMap(CoreProtoMapper.fromProtoMove).toList
    catch
      case ex: Exception =>
        log.warnf(ex, "Rule gRPC legalMoves failed")
        // scalafix:off DisableSyntax.throw
        throw ex
      // scalafix:on DisableSyntax.throw

  def allLegalMoves(ctx: GameContext): List[Move] =
    try
      stub
        .allLegalMoves(CoreProtoMapper.toProtoGameContext(ctx))
        .getMovesList
        .asScala
        .flatMap(CoreProtoMapper.fromProtoMove)
        .toList
    catch
      case ex: Exception =>
        log.warnf(ex, "Rule gRPC allLegalMoves failed")
        // scalafix:off DisableSyntax.throw
        throw ex
      // scalafix:on DisableSyntax.throw

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
    try
      val req = ProtoMoveRequest
        .newBuilder()
        .setContext(CoreProtoMapper.toProtoGameContext(ctx))
        .setMove(CoreProtoMapper.toProtoMove(move))
        .build()
      CoreProtoMapper.fromProtoGameContext(stub.applyMove(req))
    catch
      case ex: Exception =>
        log.warnf(ex, "Rule gRPC applyMove failed")
        // scalafix:off DisableSyntax.throw
        throw ex
      // scalafix:on DisableSyntax.throw

  override def postMoveStatus(ctx: GameContext): PostMoveStatus =
    try
      val p = stub.postMoveStatus(CoreProtoMapper.toProtoGameContext(ctx))
      PostMoveStatus(
        p.getIsCheckmate,
        p.getIsStalemate,
        p.getIsInsufficientMaterial,
        p.getIsCheck,
        p.getIsThreefoldRepetition,
      )
    catch
      case ex: Exception =>
        log.warnf(ex, "Rule gRPC postMoveStatus failed")
        // scalafix:off DisableSyntax.throw
        throw ex
      // scalafix:on DisableSyntax.throw
