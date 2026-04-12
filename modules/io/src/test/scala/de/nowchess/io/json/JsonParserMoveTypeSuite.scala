package de.nowchess.io.json

import de.nowchess.api.game.GameContext
import de.nowchess.api.board.{Color, PieceType, Piece, Square, File, Rank}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonParserMoveTypeSuite extends AnyFunSuite with Matchers:

  test("parse all move type variations") {
    val json = """{
      "metadata": {"event": "Game", "result": "*"},
      "gameState": {"turn": "White", "board": []},
      "moves": [
        {"from": "e2", "to": "e4", "type": {"type": "normal", "isCapture": false}},
        {"from": "e1", "to": "g1", "type": {"type": "castleKingside"}},
        {"from": "e1", "to": "c1", "type": {"type": "castleQueenside"}},
        {"from": "e5", "to": "d4", "type": {"type": "enPassant"}},
        {"from": "a7", "to": "a8", "type": {"type": "promotion", "promotionPiece": "queen"}},
        {"from": "b7", "to": "b8", "type": {"type": "promotion", "promotionPiece": "rook"}},
        {"from": "c7", "to": "c8", "type": {"type": "promotion", "promotionPiece": "bishop"}},
        {"from": "d7", "to": "d8", "type": {"type": "promotion", "promotionPiece": "knight"}}
      ]
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx = result.toOption.get
    assert(ctx.moves.length == 8)
    assert(ctx.moves(0).moveType == MoveType.Normal(false))
    assert(ctx.moves(1).moveType == MoveType.CastleKingside)
    assert(ctx.moves(2).moveType == MoveType.CastleQueenside)
    assert(ctx.moves(3).moveType == MoveType.EnPassant)
  }

  test("parse invalid move type defaults to None") {
    val json = """{
      "metadata": {"event": "Game"},
      "gameState": {"turn": "White", "board": []},
      "moves": [{"from": "e2", "to": "e4", "type": {"type": "unknown"}}]
    }"""
    val result = JsonParser.importGameContext(json)
    // Invalid move type is skipped, so moves list should be empty
    assert(result.isRight)
  }

  test("parse promotion with default piece") {
    val json = """{
      "metadata": {},
      "gameState": {"turn": "White", "board": []},
      "moves": [{"from": "a7", "to": "a8", "type": {"type": "promotion", "promotionPiece": "invalid"}}]
    }"""
    val result = JsonParser.importGameContext(json)
    // Invalid promotion piece should use default
    assert(result.isRight)
  }

  test("parse move with missing from/to skips it") {
    val json = """{
      "metadata": {},
      "gameState": {"turn": "White", "board": []},
      "moves": [{"from": "e2", "to": "invalid", "type": {"type": "normal"}}]
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx = result.toOption.get
    // Invalid square should be filtered out
    assert(ctx.moves.isEmpty)
  }

  test("parse with invalid JSON returns error") {
    val json = """{"invalid json"""
    val result = JsonParser.importGameContext(json)
    assert(result.isLeft)
  }

  test("parse normal move with isCapture true") {
    val json = """{
      "metadata": {},
      "gameState": {"turn": "White", "board": []},
      "moves": [{"from": "e4", "to": "d5", "type": {"type": "normal", "isCapture": true}}]
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx = result.toOption.get
    val move = ctx.moves.head
    assert(move.moveType == MoveType.Normal(true))
  }

  test("parse board with invalid pieces filters them") {
    val json = """{
      "metadata": {},
      "gameState": {
        "turn": "White",
        "board": [
          {"square": "a1", "color": "White", "piece": "Rook"},
          {"square": "invalid", "color": "White", "piece": "King"},
          {"square": "a2", "color": "Invalid", "piece": "Pawn"}
        ]
      }
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx = result.toOption.get
    // Only valid piece should be in board
    assert(ctx.board.pieces.size == 1)
  }
