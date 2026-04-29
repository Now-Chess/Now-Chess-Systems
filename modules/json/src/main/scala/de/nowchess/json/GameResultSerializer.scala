package de.nowchess.json

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import de.nowchess.api.game.GameResult

class GameResultSerializer extends JsonSerializer[GameResult]:
  override def serialize(value: GameResult, gen: JsonGenerator, provider: SerializerProvider): Unit =
    gen.writeStartObject()
    value match
      case GameResult.Win(color, winReason) =>
        gen.writeStringField("type", "win")
        gen.writeStringField("color", color.toString)
        gen.writeStringField("winReason", winReason.toString)
      case GameResult.Draw(reason) =>
        gen.writeStringField("type", "draw")
        gen.writeStringField("reason", reason.toString)
    gen.writeEndObject()
