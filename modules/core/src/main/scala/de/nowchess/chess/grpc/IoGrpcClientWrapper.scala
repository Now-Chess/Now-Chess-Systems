package de.nowchess.chess.grpc

import de.nowchess.api.game.GameContext
import de.nowchess.chess.client.CombinedExportResponse
import de.nowchess.core.proto.*
import io.quarkus.grpc.GrpcClient
import jakarta.enterprise.context.ApplicationScoped

import scala.compiletime.uninitialized

@ApplicationScoped
class IoGrpcClientWrapper:

  // scalafix:off DisableSyntax.var
  @GrpcClient("io-grpc")
  var stub: IoServiceGrpc.IoServiceBlockingStub = uninitialized
  // scalafix:on DisableSyntax.var

  def exportCombined(ctx: GameContext): CombinedExportResponse =
    val combined = stub.exportCombined(CoreProtoMapper.toProtoGameContext(ctx))
    CombinedExportResponse(combined.getFen, combined.getPgn)

  def importFen(fen: String): GameContext =
    CoreProtoMapper.fromProtoGameContext(stub.importFen(ProtoImportFenRequest.newBuilder().setFen(fen).build()))

  def importPgn(pgn: String): GameContext =
    CoreProtoMapper.fromProtoGameContext(stub.importPgn(ProtoImportPgnRequest.newBuilder().setPgn(pgn).build()))

  def exportFen(ctx: GameContext): String =
    stub.exportFen(CoreProtoMapper.toProtoGameContext(ctx)).getValue

  def exportPgn(ctx: GameContext): String =
    stub.exportPgn(CoreProtoMapper.toProtoGameContext(ctx)).getValue
