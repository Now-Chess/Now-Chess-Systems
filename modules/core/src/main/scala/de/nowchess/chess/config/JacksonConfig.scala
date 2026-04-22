package de.nowchess.chess.config

import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import de.nowchess.api.board.Square
import de.nowchess.api.move.MoveType
import de.nowchess.chess.json.{MoveTypeDeserializer, MoveTypeSerializer, SquareDeserializer, SquareSerializer}
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
    val mod = new SimpleModule()
    mod.addKeySerializer(classOf[Square], new SquareKeySerializer())
    mod.addKeyDeserializer(classOf[Square], new SquareKeyDeserializer())
    mod.addSerializer(classOf[Square], new SquareSerializer())
    mod.addDeserializer(classOf[Square], new SquareDeserializer())
    mod.addSerializer(classOf[MoveType], new MoveTypeSerializer())
    mod.addDeserializer(classOf[MoveType], new MoveTypeDeserializer())
    mapper.registerModule(mod)
