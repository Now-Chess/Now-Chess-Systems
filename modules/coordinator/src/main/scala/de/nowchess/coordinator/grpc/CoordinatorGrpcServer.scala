package de.nowchess.coordinator.grpc

import jakarta.inject.Inject
import jakarta.inject.Singleton
import io.quarkus.grpc.GrpcService
import scala.compiletime.uninitialized
import de.nowchess.coordinator.service.{FailoverService, InstanceRegistry}
import de.nowchess.coordinator.proto.{CoordinatorServiceGrpc, *}
import io.grpc.stub.StreamObserver
import com.fasterxml.jackson.databind.ObjectMapper
import org.jboss.logging.Logger

@GrpcService
@Singleton
class CoordinatorGrpcServer extends CoordinatorServiceGrpc.CoordinatorServiceImplBase:
  // scalafix:off DisableSyntax.var
  @Inject
  private var instanceRegistry: InstanceRegistry = uninitialized

  @Inject
  private var failoverService: FailoverService = uninitialized
  // scalafix:on DisableSyntax.var

  private val mapper = ObjectMapper()
  private val log    = Logger.getLogger(classOf[CoordinatorGrpcServer])

  override def heartbeatStream(
      responseObserver: StreamObserver[CoordinatorCommand],
  ): StreamObserver[HeartbeatFrame] =
    new StreamObserver[HeartbeatFrame]:
      // scalafix:off DisableSyntax.var
      private var lastInstanceId = ""
      // scalafix:on DisableSyntax.var

      override def onNext(frame: HeartbeatFrame): Unit =
        lastInstanceId = frame.getInstanceId
        instanceRegistry
          .updateInstanceFromRedis(frame.getInstanceId)
          .subscribe()
          .`with`(
            _ =>
              log.debugf(
                "Received heartbeat from %s with %d subscriptions",
                frame.getInstanceId,
                frame.getSubscriptionCount,
              ),
            ex => log.warnf(ex, "Failed to process heartbeat from %s", frame.getInstanceId),
          )

      override def onError(t: Throwable): Unit =
        log.warnf(t, "Heartbeat stream error for instance %s", lastInstanceId)
        if lastInstanceId.nonEmpty then failoverService.onInstanceStreamDropped(lastInstanceId)

      override def onCompleted: Unit =
        log.infof("Heartbeat stream completed for instance %s", lastInstanceId)

  override def batchResubscribeGames(
      request: BatchResubscribeRequest,
      responseObserver: StreamObserver[BatchResubscribeResponse],
  ): Unit =
    log.infof("Batch resubscribe request for %d games", request.getGameIdsList.size())
    val response = BatchResubscribeResponse
      .newBuilder()
      .setSubscribedCount(request.getGameIdsList.size())
      .build()
    responseObserver.onNext(response)
    responseObserver.onCompleted()

  override def unsubscribeGames(
      request: UnsubscribeGamesRequest,
      responseObserver: StreamObserver[UnsubscribeGamesResponse],
  ): Unit =
    log.infof("Unsubscribe request for %d games", request.getGameIdsList.size())
    val response = UnsubscribeGamesResponse
      .newBuilder()
      .setUnsubscribedCount(request.getGameIdsList.size())
      .build()
    responseObserver.onNext(response)
    responseObserver.onCompleted()

  override def evictGames(
      request: EvictGamesRequest,
      responseObserver: StreamObserver[EvictGamesResponse],
  ): Unit =
    log.infof("Evict request for %d games", request.getGameIdsList.size())
    val response = EvictGamesResponse
      .newBuilder()
      .setEvictedCount(request.getGameIdsList.size())
      .build()
    responseObserver.onNext(response)
    responseObserver.onCompleted()

  override def drainInstance(
      request: DrainInstanceRequest,
      responseObserver: StreamObserver[DrainInstanceResponse],
  ): Unit =
    val instanceId = request.getInstanceId
    log.infof("Drain request for instance %s", instanceId)
    val gamesBefore = instanceRegistry.getInstance(instanceId).map(_.subscriptionCount).getOrElse(0)
    failoverService.onInstanceStreamDropped(instanceId)
    val response = DrainInstanceResponse.newBuilder().setGamesMigrated(gamesBefore).build()
    responseObserver.onNext(response)
    responseObserver.onCompleted()
