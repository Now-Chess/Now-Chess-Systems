package de.nowchess.io.grpc

import de.nowchess.io.fen.{FenExporter, FenParser}
import de.nowchess.io.pgn.{PgnExporter, PgnParser}
import de.nowchess.io.proto.*
import io.grpc.stub.StreamObserver
import io.grpc.Status
import io.quarkus.grpc.GrpcService

import scala.jdk.CollectionConverters.*

@GrpcService
class IoGrpcService extends IoServiceGrpc.IoServiceImplBase:

  override def importFen(req: ProtoImportFenRequest, resp: StreamObserver[ProtoGameContext]): Unit =
    FenParser.parseFen(req.getFen) match
      case Left(err) =>
        resp.onError(Status.INVALID_ARGUMENT.withDescription(err.message).asRuntimeException())
      case Right(ctx) =>
        respond(resp, IoProtoMapper.toProtoGameContext(ctx))

  override def importPgn(req: ProtoImportPgnRequest, resp: StreamObserver[ProtoGameContext]): Unit =
    PgnParser.importGameContext(req.getPgn) match
      case Left(err) =>
        resp.onError(Status.INVALID_ARGUMENT.withDescription(err.message).asRuntimeException())
      case Right(ctx) =>
        respond(resp, IoProtoMapper.toProtoGameContext(ctx))

  override def exportCombined(req: ProtoGameContext, resp: StreamObserver[ProtoCombinedExport]): Unit =
    val ctx = IoProtoMapper.fromProtoGameContext(req)
    respond(
      resp,
      ProtoCombinedExport
        .newBuilder()
        .setFen(FenExporter.exportGameContext(ctx))
        .setPgn(PgnExporter.exportGameContext(ctx))
        .build(),
    )

  override def exportFen(req: ProtoGameContext, resp: StreamObserver[ProtoStringResult]): Unit =
    respond(
      resp,
      ProtoStringResult
        .newBuilder()
        .setValue(FenExporter.exportGameContext(IoProtoMapper.fromProtoGameContext(req)))
        .build(),
    )

  override def exportPgn(req: ProtoGameContext, resp: StreamObserver[ProtoStringResult]): Unit =
    respond(
      resp,
      ProtoStringResult
        .newBuilder()
        .setValue(PgnExporter.exportGameContext(IoProtoMapper.fromProtoGameContext(req)))
        .build(),
    )

  private def respond[T](obs: StreamObserver[T], value: T): Unit =
    obs.onNext(value)
    obs.onCompleted()
