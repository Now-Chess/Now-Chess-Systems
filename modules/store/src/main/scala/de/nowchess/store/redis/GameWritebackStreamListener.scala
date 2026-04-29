package de.nowchess.store.redis

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.dto.GameWritebackEventDto
import de.nowchess.store.service.GameWritebackService
import io.quarkus.redis.datasource.RedisDataSource
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import scala.compiletime.uninitialized
import scala.util.Try
import java.util.function.Consumer

@ApplicationScoped
class GameWritebackStreamListener:
  @Inject
  // scalafix:off DisableSyntax.var
  var redis: RedisDataSource                         = uninitialized
  @Inject var objectMapper: ObjectMapper             = uninitialized
  @Inject var writebackService: GameWritebackService = uninitialized
  // scalafix:on

  @PostConstruct
  def startListening(): Unit =
    val handler: Consumer[String] = json =>
      Try(objectMapper.readValue(json, classOf[GameWritebackEventDto])).toOption
        .foreach(writebackService.writeBack)
    redis.pubsub(classOf[String]).subscribe("game-writeback", handler)
    ()
