package de.nowchess.store.service

import de.nowchess.store.config.RedisConfig
import de.nowchess.store.repository.GameRecordRepository
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jboss.logging.Logger
import scala.compiletime.uninitialized

@ApplicationScoped
class ClockExpiryScanner:
  @Inject
  // scalafix:off DisableSyntax.var
  var repository: GameRecordRepository = uninitialized
  @Inject var redis: RedisDataSource   = uninitialized
  @Inject var redisConfig: RedisConfig = uninitialized
  // scalafix:on

  private val log = Logger.getLogger(classOf[ClockExpiryScanner])

  private def clockExpireChannel: String = s"${redisConfig.prefix}:game:clock:expire"

  @Scheduled(every = "30s")
  def scan(): Unit =
    try
      val nowMs   = System.currentTimeMillis()
      val expired = repository.findExpiredLiveClockGames(nowMs)
      if expired.nonEmpty then
        log.infof("Found %d games with expired clocks", expired.size)
        expired.foreach { record =>
          log.infof("Publishing clock expiry for game %s", record.gameId)
          redis.pubsub(classOf[String]).publish(clockExpireChannel, record.gameId)
        }
    catch case ex: Exception => log.warnf(ex, "Clock expiry scan failed")
