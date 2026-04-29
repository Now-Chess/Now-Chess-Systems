package de.nowchess.bot.config

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import scala.compiletime.uninitialized

@ApplicationScoped
class RedisConfig:
  // scalafix:off DisableSyntax.var
  @ConfigProperty(name = "nowchess.redis.prefix", defaultValue = "nowchess")
  var prefix: String = uninitialized
  // scalafix:on DisableSyntax.var
