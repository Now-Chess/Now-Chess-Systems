package de.nowchess.chess.grpc

import jakarta.inject.Inject
import jakarta.inject.Singleton
import io.quarkus.grpc.GrpcService
import scala.compiletime.uninitialized
import de.nowchess.coordinator.proto.{CoordinatorServiceGrpc, *}
import de.nowchess.chess.redis.GameRedisSubscriberManager
import io.grpc.stub.StreamObserver
import org.jboss.logging.Logger
import scala.jdk.CollectionConverters.*

@GrpcService
@Singleton
class CoordinatorServiceHandler extends CoordinatorServiceGrpc.CoordinatorServiceImplBase:

  private val log = Logger.getLogger(classOf[CoordinatorServiceHandler])

  // scalafix:off DisableSyntax.var
  @Inject
  private var gameSubscriberManager: GameRedisSubscriberManager = uninitialized
  // scalafix:on DisableSyntax.var

  override def batchResubscribeGames(
      request: BatchResubscribeRequest,
      responseObserver: StreamObserver[BatchResubscribeResponse],
  ): Unit =
    val count = gameSubscriberManager.batchResubscribeGames(request.getGameIdsList)
    log.infof("Coordinator: batch resubscribe %d games → subscribed %d", request.getGameIdsList.size(), count)
    val response = BatchResubscribeResponse
      .newBuilder()
      .setSubscribedCount(count)
      .build()
    responseObserver.onNext(response)
    responseObserver.onCompleted()

  override def unsubscribeGames(
      request: UnsubscribeGamesRequest,
      responseObserver: StreamObserver[UnsubscribeGamesResponse],
  ): Unit =
    val count = gameSubscriberManager.unsubscribeGames(request.getGameIdsList)
    log.infof("Coordinator: unsubscribe %d games → unsubscribed %d", request.getGameIdsList.size(), count)
    val response = UnsubscribeGamesResponse
      .newBuilder()
      .setUnsubscribedCount(count)
      .build()
    responseObserver.onNext(response)
    responseObserver.onCompleted()

  override def evictGames(
      request: EvictGamesRequest,
      responseObserver: StreamObserver[EvictGamesResponse],
  ): Unit =
    val count = gameSubscriberManager.evictGames(request.getGameIdsList)
    log.infof("Coordinator: evict %d games → evicted %d", request.getGameIdsList.size(), count)
    val response = EvictGamesResponse
      .newBuilder()
      .setEvictedCount(count)
      .build()
    responseObserver.onNext(response)
    responseObserver.onCompleted()

  override def drainInstance(
      request: DrainInstanceRequest,
      responseObserver: StreamObserver[DrainInstanceResponse],
  ): Unit =
    val migrated = gameSubscriberManager.drainInstance()
    log.infof("Coordinator: drain instance → migrated %d games", migrated)
    val response = DrainInstanceResponse
      .newBuilder()
      .setGamesMigrated(migrated)
      .build()
    responseObserver.onNext(response)
    responseObserver.onCompleted()
