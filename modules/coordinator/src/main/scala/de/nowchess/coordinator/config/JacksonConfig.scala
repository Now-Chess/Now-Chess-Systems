package de.nowchess.coordinator.config

import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.quarkus.jackson.ObjectMapperCustomizer
import jakarta.inject.Singleton

@Singleton
class JacksonConfig extends ObjectMapperCustomizer:
  def customize(mapper: ObjectMapper): Unit =
    mapper.registerModule(new DefaultScalaModule() {
      override def version(): Version =
        // scalafix:off DisableSyntax.null
        new Version(2, 21, 1, null, "com.fasterxml.jackson.module", "jackson-module-scala")
        // scalafix:on DisableSyntax.null
    })
