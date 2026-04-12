package de.nowchess.io.json

import de.nowchess.api.game.GameContext
import de.nowchess.api.board.{Color, PieceType}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonParserEdgeCasesSuite extends AnyFunSuite with Matchers:

  test("parse invalid turn color returns error") {
    val json   = """{
      "metadata": {},
      "gameState": {"turn": "Invalid", "board": []},
      "moves": []
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("Invalid turn color"))
  }

  test("parse invalid piece type filters it out") {
    val json   = """{
      "metadata": {},
      "gameState": {
        "turn": "White",
        "board": [
          {"square": "a1", "color": "White", "piece": "InvalidPiece"}
        ]
      },
      "moves": []
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx = result.toOption.get
    assert(ctx.board.pieces.isEmpty)
  }

  test("parse invalid color in board filters piece") {
    val json   = """{
      "metadata": {},
      "gameState": {
        "turn": "White",
        "board": [
          {"square": "a1", "color": "InvalidColor", "piece": "Pawn"}
        ]
      },
      "moves": []
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx = result.toOption.get
    assert(ctx.board.pieces.isEmpty)
  }

  test("parse with missing turn uses default") {
    val json   = """{
      "metadata": {},
      "gameState": {"board": []},
      "moves": []
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx = result.toOption.get
    assert(ctx.turn == Color.White)
  }

  test("parse with missing board uses empty") {
    val json   = """{
      "metadata": {},
      "gameState": {"turn": "White"},
      "moves": []
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx = result.toOption.get
    assert(ctx.board.pieces.isEmpty)
  }

  test("parse with missing moves uses empty list") {
    val json   = """{
      "metadata": {},
      "gameState": {"turn": "White", "board": []}
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx = result.toOption.get
    assert(ctx.moves.isEmpty)
  }

  test("parse invalid square in board filters it") {
    val json   = """{
      "metadata": {},
      "gameState": {
        "turn": "White",
        "board": [
          {"square": "invalid99", "color": "White", "piece": "Pawn"}
        ]
      },
      "moves": []
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx = result.toOption.get
    assert(ctx.board.pieces.isEmpty)
  }

  test("parse all valid piece types") {
    val json   = """{
      "metadata": {},
      "gameState": {
        "turn": "White",
        "board": [
          {"square": "a1", "color": "White", "piece": "Pawn"},
          {"square": "b1", "color": "White", "piece": "Knight"},
          {"square": "c1", "color": "White", "piece": "Bishop"},
          {"square": "d1", "color": "White", "piece": "Rook"},
          {"square": "e1", "color": "White", "piece": "Queen"},
          {"square": "f1", "color": "White", "piece": "King"}
        ]
      },
      "moves": []
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx = result.toOption.get
    assert(ctx.board.pieces.size == 6)
    assert(
      ctx.board
        .pieceAt(de.nowchess.api.board.Square(de.nowchess.api.board.File.A, de.nowchess.api.board.Rank.R1))
        .get
        .pieceType == PieceType.Pawn,
    )
  }

  test("parse with all castling rights false") {
    val json   = """{
      "metadata": {},
      "gameState": {
        "turn": "White",
        "board": [],
        "castlingRights": {
          "whiteKingSide": false,
          "whiteQueenSide": false,
          "blackKingSide": false,
          "blackQueenSide": false
        }
      },
      "moves": []
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx = result.toOption.get
    assert(ctx.castlingRights.whiteKingSide == false)
    assert(ctx.castlingRights.blackQueenSide == false)
  }
