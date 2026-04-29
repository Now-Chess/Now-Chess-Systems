package de.nowchess.json

import com.fasterxml.jackson.core.{JsonParseException, JsonParser}
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer}
import de.nowchess.api.board.Color
import de.nowchess.api.game.{DrawReason, GameResult, WinReason}

class GameResultDeserializer extends JsonDeserializer[GameResult]:
  // scalafix:off DisableSyntax.throw
  override def deserialize(p: JsonParser, ctx: DeserializationContext): GameResult =
    val node = p.getCodec.readTree[ObjectNode](p)
    node.get("type").asText() match
      case "win" =>
        GameResult.Win(
          Color.valueOf(node.get("color").asText()),
          WinReason.valueOf(node.get("winReason").asText()),
        )
      case "draw" => GameResult.Draw(DrawReason.valueOf(node.get("reason").asText()))
      case t      => throw new JsonParseException(p, s"Unknown game result type: $t")
  // scalafix:on DisableSyntax.throw
