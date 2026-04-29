package de.nowchess.rules.grpc

import de.nowchess.api.board.Square
import de.nowchess.rules.proto.*
import de.nowchess.rules.sets.DefaultRules
import io.grpc.stub.StreamObserver
import io.grpc.{Status, StatusRuntimeException}
import io.quarkus.grpc.GrpcService

// scalafix:off DisableSyntax.throw
@GrpcService
class RuleGrpcService extends RuleServiceGrpc.RuleServiceImplBase:

  private def parseSquare(s: String): Square =
    Square
      .fromAlgebraic(s)
      .getOrElse(
        throw Status.INVALID_ARGUMENT.withDescription(s"Invalid square: $s").asRuntimeException(),
      )

  override def candidateMoves(req: ProtoSquareRequest, resp: StreamObserver[ProtoMoveList]): Unit =
    val ctx   = ProtoMapper.fromProtoGameContext(req.getContext)
    val sq    = parseSquare(req.getSquare)
    val moves = DefaultRules.candidateMoves(ctx)(sq)
    resp.onNext(
      ProtoMoveList
        .newBuilder()
        .addAllMoves(toJavaMoveList(moves))
        .build(),
    )
    resp.onCompleted()

  override def legalMoves(req: ProtoSquareRequest, resp: StreamObserver[ProtoMoveList]): Unit =
    val ctx   = ProtoMapper.fromProtoGameContext(req.getContext)
    val sq    = parseSquare(req.getSquare)
    val moves = DefaultRules.legalMoves(ctx)(sq)
    respond(resp, ProtoMoveList.newBuilder().addAllMoves(toJavaMoveList(moves)).build())

  override def allLegalMoves(req: ProtoGameContext, resp: StreamObserver[ProtoMoveList]): Unit =
    val moves = DefaultRules.allLegalMoves(ProtoMapper.fromProtoGameContext(req))
    respond(resp, ProtoMoveList.newBuilder().addAllMoves(toJavaMoveList(moves)).build())

  override def isCheck(req: ProtoGameContext, resp: StreamObserver[ProtoBoolResult]): Unit =
    respond(resp, boolResult(DefaultRules.isCheck(ProtoMapper.fromProtoGameContext(req))))

  override def isCheckmate(req: ProtoGameContext, resp: StreamObserver[ProtoBoolResult]): Unit =
    respond(resp, boolResult(DefaultRules.isCheckmate(ProtoMapper.fromProtoGameContext(req))))

  override def isStalemate(req: ProtoGameContext, resp: StreamObserver[ProtoBoolResult]): Unit =
    respond(resp, boolResult(DefaultRules.isStalemate(ProtoMapper.fromProtoGameContext(req))))

  override def isInsufficientMaterial(req: ProtoGameContext, resp: StreamObserver[ProtoBoolResult]): Unit =
    respond(resp, boolResult(DefaultRules.isInsufficientMaterial(ProtoMapper.fromProtoGameContext(req))))

  override def isFiftyMoveRule(req: ProtoGameContext, resp: StreamObserver[ProtoBoolResult]): Unit =
    respond(resp, boolResult(DefaultRules.isFiftyMoveRule(ProtoMapper.fromProtoGameContext(req))))

  override def isThreefoldRepetition(req: ProtoGameContext, resp: StreamObserver[ProtoBoolResult]): Unit =
    respond(resp, boolResult(DefaultRules.isThreefoldRepetition(ProtoMapper.fromProtoGameContext(req))))

  override def applyMove(req: ProtoMoveRequest, resp: StreamObserver[ProtoGameContext]): Unit =
    val ctx = ProtoMapper.fromProtoGameContext(req.getContext)
    val move = ProtoMapper
      .fromProtoMove(req.getMove)
      .getOrElse(
        throw Status.INVALID_ARGUMENT.withDescription("Invalid move").asRuntimeException(),
      )
    respond(resp, ProtoMapper.toProtoGameContext(DefaultRules.applyMove(ctx)(move)))

  override def postMoveStatus(req: ProtoGameContext, resp: StreamObserver[ProtoPostMoveStatus]): Unit =
    val status = DefaultRules.postMoveStatus(ProtoMapper.fromProtoGameContext(req))
    respond(
      resp,
      ProtoPostMoveStatus
        .newBuilder()
        .setIsCheckmate(status.isCheckmate)
        .setIsStalemate(status.isStalemate)
        .setIsInsufficientMaterial(status.isInsufficientMaterial)
        .setIsCheck(status.isCheck)
        .setIsThreefoldRepetition(status.isThreefoldRepetition)
        .build(),
    )

  private def boolResult(v: Boolean): ProtoBoolResult = ProtoBoolResult.newBuilder().setValue(v).build()

  private def respond[T](obs: StreamObserver[T], value: T): Unit =
    obs.onNext(value)
    obs.onCompleted()

  private def toJavaMoveList(moves: List[de.nowchess.api.move.Move]): java.util.List[ProtoMove] =
    import scala.jdk.CollectionConverters.*
    moves.map(ProtoMapper.toProtoMove).asJava
// scalafix:on DisableSyntax.throw
