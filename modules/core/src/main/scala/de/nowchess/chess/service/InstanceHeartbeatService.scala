package de.nowchess.chess.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import io.quarkus.runtime.StartupEvent
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.grpc.GrpcClient
import org.eclipse.microprofile.config.inject.ConfigProperty
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.ReactiveRedisDataSource
import scala.compiletime.uninitialized
import java.util.concurrent.{Executors, TimeUnit}
import java.net.InetAddress
import com.fasterxml.jackson.databind.ObjectMapper
import org.jboss.logging.Logger
import de.nowchess.coordinator.proto.{CoordinatorServiceGrpc, *}
import de.nowchess.coordinator.proto.CoordinatorServiceGrpc.CoordinatorServiceStub
import io.grpc.stub.StreamObserver
import io.grpc.Channel

@ApplicationScoped
class InstanceHeartbeatService:
  // scalafix:off DisableSyntax.var
  @Inject
  private var redis: RedisDataSource = uninitialized

  @Inject
  private var reactiveRedis: ReactiveRedisDataSource = uninitialized

  @Inject
  private var mapper: ObjectMapper = uninitialized

  @GrpcClient("coordinator-grpc")
  private var channel: Channel = uninitialized

  @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
  private var httpPort: Int = 0

  @ConfigProperty(name = "quarkus.grpc.server.port", defaultValue = "9000")
  private var grpcPort: Int = 0

  @ConfigProperty(name = "nowchess.coordinator.enabled", defaultValue = "true")
  private var coordinatorEnabled: Boolean = true

  private var coordinatorStub: CoordinatorServiceStub = uninitialized
  private val log                                     = Logger.getLogger(classOf[InstanceHeartbeatService])
  private var instanceId                              = ""
  private var redisPrefix                             = "nowchess"
  private var streamObserver: Option[StreamObserver[HeartbeatFrame]] = None
  private var heartbeatExecutor                                      = Executors.newScheduledThreadPool(1)
  private var redisHeartbeatExecutor                                 = Executors.newScheduledThreadPool(1)
  private var subscriptionCount                                      = 0
  private var localCacheSize                                         = 0
  private var serviceActive                                          = false
  private var shuttingDown                                           = false
  // scalafix:on DisableSyntax.var

  def onStart(@Observes event: StartupEvent): Unit =
    if coordinatorEnabled then
      try
        shuttingDown = false
        generateInstanceId()
        initializeHeartbeatStream()
        scheduleHeartbeats()
        serviceActive = true
        log.infof("Instance heartbeat service started with ID: %s", instanceId)
      catch
        case ex: Exception =>
          serviceActive = false
          log.errorf(ex, "Failed to start instance heartbeat service")
    else log.info("Coordinator support disabled via config; skipping heartbeat service startup")

  def onShutdown(@Observes event: ShutdownEvent): Unit =
    shuttingDown = true

    if serviceActive then
      try
        cleanup()
        serviceActive = false
        log.info("Instance heartbeat service stopped")
      catch
        case ex: Exception =>
          log.errorf(ex, "Error during heartbeat service shutdown")
    else log.info("Instance heartbeat service stopped")

  def setRedisPrefix(prefix: String): Unit =
    redisPrefix = prefix

  def setSubscriptionCount(count: Int): Unit =
    subscriptionCount = count

  def setLocalCacheSize(count: Int): Unit =
    localCacheSize = count

  def addGameSubscription(gameId: String): Unit =
    if coordinatorEnabled then
      val setKey = s"$redisPrefix:instance:$instanceId:games"
      redis.set(classOf[String]).sadd(setKey, gameId)
      subscriptionCount += 1

  def removeGameSubscription(gameId: String): Unit =
    if coordinatorEnabled then
      val setKey = s"$redisPrefix:instance:$instanceId:games"
      redis.set(classOf[String]).srem(setKey, gameId)
      subscriptionCount = Math.max(0, subscriptionCount - 1)

  private def generateInstanceId(): Unit =
    val hostname =
      try InetAddress.getLocalHost.getHostName
      catch case _: Exception => "unknown"

    val uuid = java.util.UUID.randomUUID().toString.take(8)
    instanceId = s"$hostname-$uuid"

  private def initializeHeartbeatStream(): Unit =
    try
      coordinatorStub = CoordinatorServiceGrpc.newStub(channel)
      val responseObserver = new StreamObserver[CoordinatorCommand]:
        override def onNext(value: CoordinatorCommand): Unit =
          log.debugf("Received coordinator command: %s", value.getType)

        override def onError(t: Throwable): Unit =
          log.warnf(t, "Heartbeat stream error")
          streamObserver = None
          if !shuttingDown then
            heartbeatExecutor.schedule((() => initializeHeartbeatStream()): Runnable, 5, TimeUnit.SECONDS)

        override def onCompleted: Unit =
          log.info("Heartbeat stream completed")

      streamObserver = Some(coordinatorStub.heartbeatStream(responseObserver))
      log.info("Connected to coordinator heartbeat stream")
    catch
      case ex: Exception =>
        log.warnf(ex, "Failed to connect to coordinator")
        streamObserver = None

  private def scheduleHeartbeats(): Unit =
    heartbeatExecutor.scheduleAtFixedRate(
      () => sendHeartbeat(),
      0,
      200,
      TimeUnit.MILLISECONDS,
    )

    redisHeartbeatExecutor.scheduleAtFixedRate(
      () => refreshRedisHeartbeat(),
      0,
      2,
      TimeUnit.SECONDS,
    )

  private def sendHeartbeat(): Unit =
    streamObserver.foreach { observer =>
      try
        val frame = HeartbeatFrame
          .newBuilder()
          .setInstanceId(instanceId)
          .setHostname(getHostname)
          .setHttpPort(httpPort)
          .setGrpcPort(grpcPort)
          .setSubscriptionCount(subscriptionCount)
          .setLocalCacheSize(localCacheSize)
          .setTimestampMillis(System.currentTimeMillis())
          .build()
        observer.onNext(frame)
      catch
        case ex: Exception =>
          log.warnf(ex, "Failed to send heartbeat frame")
    }

  private def refreshRedisHeartbeat(): Unit =
    try
      val key = s"$redisPrefix:instances:$instanceId"

      val metadata = Map(
        "instanceId"        -> instanceId,
        "hostname"          -> getHostname,
        "httpPort"          -> httpPort,
        "grpcPort"          -> grpcPort,
        "subscriptionCount" -> subscriptionCount,
        "localCacheSize"    -> localCacheSize,
        "lastHeartbeat"     -> java.time.Instant.now().toString,
        "state"             -> "HEALTHY",
      )

      val json = mapper.writeValueAsString(metadata)
      reactiveRedis.value(classOf[String]).setex(key, 5L, json)
        .subscribe().`with`(
          _ => (),
          (ex: Throwable) => log.warnf(ex, "Failed to refresh Redis heartbeat"),
        )
    catch
      case ex: Exception =>
        log.warnf(ex, "Failed to serialize Redis heartbeat metadata")

  private def getHostname: String =
    try InetAddress.getLocalHost.getHostName
    catch case _: Exception => "unknown"

  private def cleanup(): Unit =
    streamObserver.foreach(_.onCompleted())
    streamObserver = None

    if instanceId.nonEmpty then
      val key = s"$redisPrefix:instances:$instanceId"
      redis.key(classOf[String]).del(key)

      val setKey = s"$redisPrefix:instance:$instanceId:games"
      redis.key(classOf[String]).del(setKey)

    heartbeatExecutor.shutdown()
    redisHeartbeatExecutor.shutdown()
    if !heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS) then heartbeatExecutor.shutdownNow()
    if !redisHeartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS) then redisHeartbeatExecutor.shutdownNow()
