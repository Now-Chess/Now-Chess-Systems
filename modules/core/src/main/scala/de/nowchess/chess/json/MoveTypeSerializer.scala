package de.nowchess.chess.json

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import de.nowchess.api.move.MoveType

class MoveTypeSerializer extends JsonSerializer[MoveType]:
  override def serialize(value: MoveType, gen: JsonGenerator, provider: SerializerProvider): Unit =
    gen.writeStartObject()
    value match
      case MoveType.Normal(isCapture) =>
        gen.writeStringField("type", "normal")
        gen.writeBooleanField("isCapture", isCapture)
      case MoveType.CastleKingside =>
        gen.writeStringField("type", "castleKingside")
      case MoveType.CastleQueenside =>
        gen.writeStringField("type", "castleQueenside")
      case MoveType.EnPassant =>
        gen.writeStringField("type", "enPassant")
      case MoveType.Promotion(piece) =>
        gen.writeStringField("type", "promotion")
        gen.writeStringField("piece", piece.toString)
    gen.writeEndObject()
