package de.nowchess.json

import com.fasterxml.jackson.core.{JsonParseException, JsonParser}
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer}
import de.nowchess.api.move.{MoveType, PromotionPiece}

class MoveTypeDeserializer extends JsonDeserializer[MoveType]:
  // scalafix:off DisableSyntax.throw
  override def deserialize(p: JsonParser, ctx: DeserializationContext): MoveType =
    val node = p.getCodec.readTree[ObjectNode](p)
    node.get("type").asText() match
      case "normal"          => MoveType.Normal(node.get("isCapture").asBoolean(false))
      case "castleKingside"  => MoveType.CastleKingside
      case "castleQueenside" => MoveType.CastleQueenside
      case "enPassant"       => MoveType.EnPassant
      case "promotion"       => MoveType.Promotion(PromotionPiece.valueOf(node.get("piece").asText()))
      case t                 => throw new JsonParseException(p, s"Unknown move type: $t")
  // scalafix:on DisableSyntax.throw
