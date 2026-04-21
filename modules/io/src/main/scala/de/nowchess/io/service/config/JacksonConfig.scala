package de.nowchess.io.service.config

import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import de.nowchess.api.board.Square
import de.nowchess.io.json.{SquareKeyDeserializer, SquareKeySerializer}
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
    val squareModule = new SimpleModule()
    squareModule.addKeyDeserializer(classOf[Square], new SquareKeyDeserializer())
    squareModule.addKeySerializer(classOf[Square], new SquareKeySerializer())
    mapper.registerModule(squareModule)
