package de.nowchess.coordinator.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import de.nowchess.coordinator.config.CoordinatorConfig
import io.quarkus.redis.datasource.RedisDataSource
import org.jboss.logging.Logger
import scala.compiletime.uninitialized
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import de.nowchess.coordinator.grpc.CoreGrpcClient

@ApplicationScoped
class LoadBalancer:
  // scalafix:off DisableSyntax.var
  @Inject
  private var config: CoordinatorConfig = uninitialized

  @Inject
  private var instanceRegistry: InstanceRegistry = uninitialized

  @Inject
  private var redis: RedisDataSource = uninitialized

  @Inject
  private var coreGrpcClient: CoreGrpcClient = uninitialized

  private val log               = Logger.getLogger(classOf[LoadBalancer])
  private val lastRebalanceTime = new java.util.concurrent.atomic.AtomicLong(0L)
  private var redisPrefix       = "nowchess"
  // scalafix:on DisableSyntax.var

  def setRedisPrefix(prefix: String): Unit =
    redisPrefix = prefix

  def shouldRebalance: Boolean =
    val now         = System.currentTimeMillis()
    val minInterval = config.rebalanceMinInterval.toMillis
    if now - lastRebalanceTime.get() < minInterval then false
    else
      val instances = instanceRegistry.getAllInstances
      if instances.isEmpty then false
      else
        val loads   = instances.map(_.subscriptionCount)
        val maxLoad = loads.max
        val minLoad = loads.min
        val avgLoad = loads.sum.toDouble / loads.size

        val exceededMax      = maxLoad > config.maxGamesPerCore
        val deviationPercent = 100.0 * (maxLoad - avgLoad) / avgLoad
        val exceededDeviation =
          maxLoad > avgLoad && deviationPercent > config.maxDeviationPercent && (maxLoad - minLoad) > 50

        exceededMax || exceededDeviation

  def rebalance: Unit =
    log.info("Starting rebalance")
    val startTime = System.currentTimeMillis()
    lastRebalanceTime.set(startTime)

    try
      val instances = instanceRegistry.getAllInstances.filter(_.state == "HEALTHY")

      if instances.size < 2 then log.info("Not enough healthy instances for rebalance")
      else
        val loads   = instances.map(_.subscriptionCount)
        val avgLoad = loads.sum.toDouble / loads.size

        val overloaded = instances
          .filter(_.subscriptionCount > config.maxGamesPerCore)
          .sortBy[Int](_.subscriptionCount)
          .reverse
        val underloaded = instances
          .filter(_.subscriptionCount < avgLoad * 0.8)
          .sortBy(_.subscriptionCount)

        if underloaded.isEmpty then log.info("No underloaded instances available for rebalance")
        else
          val allBatches = overloaded.flatMap { over =>
            val excess      = math.max(0, over.subscriptionCount - avgLoad.toInt)
            val gamesToMove = getGamesToMove(over.instanceId, excess)
            if gamesToMove.isEmpty then List.empty
            else
              val batchSize = math.max(1, (gamesToMove.size + underloaded.size - 1) / underloaded.size)
              gamesToMove.grouped(batchSize).toList.map((over, _))
          }

          allBatches.zipWithIndex.foreach { case ((over, batch), idx) =>
            val target = underloaded(idx % underloaded.size)
            try
              coreGrpcClient.unsubscribeGames(over.hostname, over.grpcPort, batch)
              val subscribed = coreGrpcClient.batchResubscribeGames(target.hostname, target.grpcPort, batch)
              if subscribed > 0 then
                updateRedisGameSets(over.instanceId, target.instanceId, batch)
                log.infof("Moved %d games from %s to %s", subscribed, over.instanceId, target.instanceId)
            catch
              case ex: Exception =>
                log.warnf(ex, "Failed to move games from %s to %s", over.instanceId, target.instanceId)
          }

          val elapsed = System.currentTimeMillis() - startTime
          log.infof("Rebalance completed in %dms", elapsed)
    catch
      case ex: Exception =>
        log.warnf(ex, "Rebalance failed")

  private def getGamesToMove(instanceId: String, count: Int): List[String] =
    try
      val setKey = s"$redisPrefix:instance:$instanceId:games"
      redis.set(classOf[String]).smembers(setKey).asScala.toList.take(count)
    catch
      case ex: Exception =>
        log.debugf(ex, "Failed to get games for %s", instanceId)
        List()

  private def updateRedisGameSets(fromInstanceId: String, toInstanceId: String, gameIds: List[String]): Unit =
    try
      val fromKey = s"$redisPrefix:instance:$fromInstanceId:games"
      val toKey   = s"$redisPrefix:instance:$toInstanceId:games"

      gameIds.foreach { gameId =>
        redis.set(classOf[String]).srem(fromKey, gameId)
        redis.set(classOf[String]).sadd(toKey, gameId)
      }
    catch
      case ex: Exception =>
        log.warnf(ex, "Failed to update Redis game sets")
