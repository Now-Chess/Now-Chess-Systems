package de.nowchess.json

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import de.nowchess.api.board.Square

class SquareKeySerializer extends JsonSerializer[Square]:
  override def serialize(value: Square, gen: JsonGenerator, provider: SerializerProvider): Unit =
    gen.writeFieldName(value.toString)
