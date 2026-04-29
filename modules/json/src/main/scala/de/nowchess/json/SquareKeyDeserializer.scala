package de.nowchess.json

import com.fasterxml.jackson.databind.{DeserializationContext, KeyDeserializer}
import de.nowchess.api.board.Square

class SquareKeyDeserializer extends KeyDeserializer:
  override def deserializeKey(key: String, ctx: DeserializationContext): AnyRef =
    Square.fromAlgebraic(key).orNull
