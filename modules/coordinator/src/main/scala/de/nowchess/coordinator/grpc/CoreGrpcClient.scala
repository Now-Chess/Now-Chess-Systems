package de.nowchess.coordinator.grpc

import jakarta.enterprise.context.ApplicationScoped
import jakarta.annotation.PreDestroy
import org.jboss.logging.Logger
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import de.nowchess.coordinator.proto.{CoordinatorServiceGrpc, *}
import scala.jdk.CollectionConverters.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@ApplicationScoped
class CoreGrpcClient:
  private val log      = Logger.getLogger(classOf[CoreGrpcClient])
  private val channels = ConcurrentHashMap[String, ManagedChannel]()

  private def getChannel(host: String, port: Int): ManagedChannel =
    channels.computeIfAbsent(s"$host:$port", _ => ManagedChannelBuilder.forAddress(host, port).usePlaintext().build())

  private def evictStaleChannel(host: String, port: Int): Unit =
    Option(channels.remove(s"$host:$port")).foreach(_.shutdownNow())

  @PreDestroy
  def shutdown(): Unit =
    channels.values.asScala.foreach { ch =>
      ch.shutdown()
      if !ch.awaitTermination(5, TimeUnit.SECONDS) then ch.shutdownNow()
    }
    channels.clear()

  def batchResubscribeGames(host: String, port: Int, gameIds: List[String]): Int =
    try
      val stub    = CoordinatorServiceGrpc.newBlockingStub(getChannel(host, port))
      val request = BatchResubscribeRequest.newBuilder().addAllGameIds(gameIds.asJava).build()
      stub.batchResubscribeGames(request).getSubscribedCount
    catch
      case ex: Exception =>
        log.warnf(ex, "batchResubscribeGames RPC failed for %s:%d", host, port)
        evictStaleChannel(host, port)
        0

  def unsubscribeGames(host: String, port: Int, gameIds: List[String]): Int =
    try
      val stub    = CoordinatorServiceGrpc.newBlockingStub(getChannel(host, port))
      val request = UnsubscribeGamesRequest.newBuilder().addAllGameIds(gameIds.asJava).build()
      stub.unsubscribeGames(request).getUnsubscribedCount
    catch
      case ex: Exception =>
        log.warnf(ex, "unsubscribeGames RPC failed for %s:%d", host, port)
        evictStaleChannel(host, port)
        0

  def evictGames(host: String, port: Int, gameIds: List[String]): Int =
    try
      val stub    = CoordinatorServiceGrpc.newBlockingStub(getChannel(host, port))
      val request = EvictGamesRequest.newBuilder().addAllGameIds(gameIds.asJava).build()
      stub.evictGames(request).getEvictedCount
    catch
      case ex: Exception =>
        log.warnf(ex, "evictGames RPC failed for %s:%d", host, port)
        evictStaleChannel(host, port)
        0
