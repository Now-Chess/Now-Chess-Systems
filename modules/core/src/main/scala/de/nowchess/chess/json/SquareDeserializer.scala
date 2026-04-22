package de.nowchess.chess.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer}
import de.nowchess.api.board.Square

class SquareDeserializer extends JsonDeserializer[Square]:
  override def deserialize(p: JsonParser, ctx: DeserializationContext): Square =
    Square.fromAlgebraic(p.getText).orNull
