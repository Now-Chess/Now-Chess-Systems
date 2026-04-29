package de.nowchess.chess.config

import io.quarkus.redis.datasource.RedisDataSource
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import jakarta.enterprise.inject.Produces
import org.mockito.Mockito

@Alternative
@Priority(1)
@ApplicationScoped
class MockRedisDataSourceProducer:
  @Produces
  @ApplicationScoped
  def produceRedisDataSource(): RedisDataSource =
    Mockito.mock(classOf[RedisDataSource], Mockito.RETURNS_DEEP_STUBS)
