package de.nowchess.chess.grpc

import de.nowchess.api.game.GameContext
import de.nowchess.chess.client.CombinedExportResponse
import de.nowchess.core.proto.*
import io.quarkus.grpc.GrpcClient
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger

import scala.compiletime.uninitialized

@ApplicationScoped
class IoGrpcClientWrapper:

  private val log = Logger.getLogger(classOf[IoGrpcClientWrapper])

  // scalafix:off DisableSyntax.var
  @GrpcClient("io-grpc")
  var stub: IoServiceGrpc.IoServiceBlockingStub = uninitialized
  // scalafix:on DisableSyntax.var

  def exportCombined(ctx: GameContext): CombinedExportResponse =
    try
      val combined = stub.exportCombined(CoreProtoMapper.toProtoGameContext(ctx))
      CombinedExportResponse(combined.getFen, combined.getPgn)
    catch
      case ex: Exception =>
        log.warnf(ex, "IO gRPC exportCombined failed")
        // scalafix:off DisableSyntax.throw
        throw ex
      // scalafix:on DisableSyntax.throw

  def importFen(fen: String): GameContext =
    try CoreProtoMapper.fromProtoGameContext(stub.importFen(ProtoImportFenRequest.newBuilder().setFen(fen).build()))
    catch
      case ex: Exception =>
        log.warnf(ex, "IO gRPC importFen failed for fen %s", fen)
        // scalafix:off DisableSyntax.throw
        throw ex
      // scalafix:on DisableSyntax.throw

  def importPgn(pgn: String): GameContext =
    try CoreProtoMapper.fromProtoGameContext(stub.importPgn(ProtoImportPgnRequest.newBuilder().setPgn(pgn).build()))
    catch
      case ex: Exception =>
        log.warnf(ex, "IO gRPC importPgn failed")
        // scalafix:off DisableSyntax.throw
        throw ex
      // scalafix:on DisableSyntax.throw

  def exportFen(ctx: GameContext): String =
    try stub.exportFen(CoreProtoMapper.toProtoGameContext(ctx)).getValue
    catch
      case ex: Exception =>
        log.warnf(ex, "IO gRPC exportFen failed")
        // scalafix:off DisableSyntax.throw
        throw ex
      // scalafix:on DisableSyntax.throw

  def exportPgn(ctx: GameContext): String =
    try stub.exportPgn(CoreProtoMapper.toProtoGameContext(ctx)).getValue
    catch
      case ex: Exception =>
        log.warnf(ex, "IO gRPC exportPgn failed")
        // scalafix:off DisableSyntax.throw
        throw ex
      // scalafix:on DisableSyntax.throw
