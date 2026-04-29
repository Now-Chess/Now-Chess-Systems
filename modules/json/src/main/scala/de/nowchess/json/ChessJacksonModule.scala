package de.nowchess.json

import com.fasterxml.jackson.databind.module.SimpleModule
import de.nowchess.api.board.Square
import de.nowchess.api.game.GameResult
import de.nowchess.api.move.MoveType

class ChessJacksonModule extends SimpleModule:
  addKeySerializer(classOf[Square], new SquareKeySerializer())
  addKeyDeserializer(classOf[Square], new SquareKeyDeserializer())
  addSerializer(classOf[Square], new SquareSerializer())
  addDeserializer(classOf[Square], new SquareDeserializer())
  addSerializer(classOf[MoveType], new MoveTypeSerializer())
  addDeserializer(classOf[MoveType], new MoveTypeDeserializer())
  addSerializer(classOf[GameResult], new GameResultSerializer())
  addDeserializer(classOf[GameResult], new GameResultDeserializer())
