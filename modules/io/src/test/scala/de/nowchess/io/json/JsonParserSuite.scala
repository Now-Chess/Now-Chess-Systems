package de.nowchess.io.json

import de.nowchess.api.game.GameContext
import de.nowchess.api.board.{Color, File, Rank, Square, CastlingRights}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonParserSuite extends AnyFunSuite with Matchers:

  test("importGameContext: parses valid JSON") {
    val json = JsonExporter.exportGameContext(GameContext.initial)
    val result = JsonParser.importGameContext(json)
    
    assert(result.isRight)
  }

  test("importGameContext: restores board state") {
    val context = GameContext.initial
    val json = JsonExporter.exportGameContext(context)
    val result = JsonParser.importGameContext(json)
    
    assert(result == Right(context))
  }

  test("importGameContext: restores turn") {
    val context = GameContext.initial.withTurn(Color.Black)
    val json = JsonExporter.exportGameContext(context)
    val result = JsonParser.importGameContext(json)
    
    assert(result.map(_.turn) == Right(Color.Black))
  }

  test("importGameContext: restores moves") {
    val move = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4))
    val context = GameContext.initial.withMove(move)
    val json = JsonExporter.exportGameContext(context)
    val result = JsonParser.importGameContext(json)
    
    assert(result.map(_.moves.length) == Right(1))
  }

  test("importGameContext: handles empty board") {
    val json = """{
  "metadata": {"event": "Game", "players": {"white": "A", "black": "B"}, "date": "2026-04-06", "result": "*"},
  "gameState": {
    "board": [],
    "turn": "White",
    "castlingRights": {"whiteKingSide": true, "whiteQueenSide": true, "blackKingSide": true, "blackQueenSide": true},
    "enPassantSquare": null,
    "halfMoveClock": 0
  },
  "moves": [],
  "moveHistory": "",
  "capturedPieces": {"byWhite": [], "byBlack": []},
  "timestamp": "2026-04-06T00:00:00Z"
}"""
    val result = JsonParser.importGameContext(json)
    
    assert(result.isRight)
    assert(result.map(_.board.pieces.isEmpty) == Right(true))
  }

  test("importGameContext: returns error on invalid JSON") {
    val result = JsonParser.importGameContext("not valid json {{{")
    
    assert(result.isLeft)
  }

  test("importGameContext: handles missing fields with defaults") {
    val json = "{\"metadata\": {}, \"gameState\": {\"board\": [], \"turn\": \"White\", \"castlingRights\": {\"whiteKingSide\": true, \"whiteQueenSide\": true, \"blackKingSide\": true, \"blackQueenSide\": true}, \"enPassantSquare\": null, \"halfMoveClock\": 0}, \"moves\": [], \"moveHistory\": \"\", \"capturedPieces\": {\"byWhite\": [], \"byBlack\": []}, \"timestamp\": \"2026-01-01T00:00:00Z\"}"
    val result = JsonParser.importGameContext(json)
    
    assert(result.isRight)
  }

  test("importGameContext: handles castling rights") {
    val newCastling = GameContext.initial.castlingRights.copy(whiteKingSide = false)
    val context = GameContext.initial.withCastlingRights(newCastling)
    val json = JsonExporter.exportGameContext(context)
    val result = JsonParser.importGameContext(json)
    
    assert(result.map(_.castlingRights.whiteKingSide) == Right(false))
  }

  test("importGameContext: round-trip consistency") {
    val move1 = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4))
    val move2 = Move(Square(File.E, Rank.R7), Square(File.E, Rank.R5))
    val context = GameContext.initial
      .withMove(move1)
      .withMove(move2)
      .withTurn(Color.White)

    val json = JsonExporter.exportGameContext(context)
    val restored = JsonParser.importGameContext(json)

    assert(restored.map(_.moves.length) == Right(2))
    assert(restored.map(_.turn) == Right(Color.White))
  }

  test("importGameContext: handles half-move clock") {
    val context = GameContext.initial.withHalfMoveClock(5)
    val json = JsonExporter.exportGameContext(context)
    val result = JsonParser.importGameContext(json)
    
    assert(result.map(_.halfMoveClock) == Right(5))
  }

  test("importGameContext: parses en passant square") {
    // Create a context with en passant square
    val epSquare = Some(Square(File.E, Rank.R3))
    val context = GameContext.initial.copy(enPassantSquare = epSquare)
    val json = JsonExporter.exportGameContext(context)
    val result = JsonParser.importGameContext(json)
    
    assert(result.map(_.enPassantSquare) == Right(epSquare))
  }

  test("importGameContext: handles black turn") {
    val context = GameContext.initial.withTurn(Color.Black)
    val json = JsonExporter.exportGameContext(context)
    val result = JsonParser.importGameContext(json)
    
    assert(result.map(_.turn) == Right(Color.Black))
  }

  test("importGameContext: preserves basic moves in JSON round-trip") {
    // Use simple move without explicit moveType to let system handle it
    val move = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4))
    val context = GameContext.initial.withMove(move)
    val json = JsonExporter.exportGameContext(context)
    val result = JsonParser.importGameContext(json)
    
    assert(result.isRight)
    assert(result.map(_.moves.length) == Right(1))
  }

  test("importGameContext: handles all castling rights disabled") {
    val noCastling = CastlingRights(false, false, false, false)
    val context = GameContext.initial.withCastlingRights(noCastling)
    val json = JsonExporter.exportGameContext(context)
    val result = JsonParser.importGameContext(json)
    
    assert(result.map(_.castlingRights) == Right(noCastling))
  }

  test("importGameContext: handles mixed castling rights") {
    val mixed = CastlingRights(true, false, false, true)
    val context = GameContext.initial.withCastlingRights(mixed)
    val json = JsonExporter.exportGameContext(context)
    val result = JsonParser.importGameContext(json)
    
    assert(result.map(_.castlingRights) == Right(mixed))
  }
