package de.nowchess.io.json

import de.nowchess.api.game.GameContext
import de.nowchess.api.board.{CastlingRights, Color, File, PieceType, Rank, Square}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonParserTest extends AnyFunSuite with Matchers:

  // Basic import tests
  test("importGameContext: parses valid JSON") {
    val json   = JsonExporter.exportGameContext(GameContext.initial)
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
  }

  test("importGameContext: restores board state") {
    val context = GameContext.initial
    val json    = JsonExporter.exportGameContext(context)
    val result  = JsonParser.importGameContext(json)
    assert(result == Right(context))
  }

  test("importGameContext: restores turn") {
    val context = GameContext.initial.withTurn(Color.Black)
    val json    = JsonExporter.exportGameContext(context)
    val result  = JsonParser.importGameContext(json)
    assert(result.map(_.turn) == Right(Color.Black))
  }

  test("importGameContext: restores moves") {
    val move    = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4))
    val context = GameContext.initial.withMove(move)
    val json    = JsonExporter.exportGameContext(context)
    val result  = JsonParser.importGameContext(json)
    assert(result.map(_.moves.length) == Right(1))
  }

  test("importGameContext: handles castling rights") {
    val newCastling = GameContext.initial.castlingRights.copy(whiteKingSide = false)
    val context     = GameContext.initial.withCastlingRights(newCastling)
    val json        = JsonExporter.exportGameContext(context)
    val result      = JsonParser.importGameContext(json)
    assert(result.map(_.castlingRights.whiteKingSide) == Right(false))
  }

  test("importGameContext: round-trip consistency with multiple moves") {
    val move1 = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4))
    val move2 = Move(Square(File.E, Rank.R7), Square(File.E, Rank.R5))
    val context = GameContext.initial
      .withMove(move1)
      .withMove(move2)
      .withTurn(Color.White)

    val json     = JsonExporter.exportGameContext(context)
    val restored = JsonParser.importGameContext(json)
    assert(restored.map(_.moves.length) == Right(2))
    assert(restored.map(_.turn) == Right(Color.White))
  }

  test("importGameContext: handles half-move clock") {
    val context = GameContext.initial.withHalfMoveClock(5)
    val json    = JsonExporter.exportGameContext(context)
    val result  = JsonParser.importGameContext(json)
    assert(result.map(_.halfMoveClock) == Right(5))
  }

  test("importGameContext: parses en passant square") {
    val epSquare = Some(Square(File.E, Rank.R3))
    val context  = GameContext.initial.copy(enPassantSquare = epSquare)
    val json     = JsonExporter.exportGameContext(context)
    val result   = JsonParser.importGameContext(json)
    assert(result.map(_.enPassantSquare) == Right(epSquare))
  }

  test("importGameContext: handles all castling rights disabled") {
    val noCastling = CastlingRights(false, false, false, false)
    val context    = GameContext.initial.withCastlingRights(noCastling)
    val json       = JsonExporter.exportGameContext(context)
    val result     = JsonParser.importGameContext(json)
    assert(result.map(_.castlingRights) == Right(noCastling))
  }

  test("importGameContext: handles mixed castling rights") {
    val mixed   = CastlingRights(true, false, false, true)
    val context = GameContext.initial.withCastlingRights(mixed)
    val json    = JsonExporter.exportGameContext(context)
    val result  = JsonParser.importGameContext(json)
    assert(result.map(_.castlingRights) == Right(mixed))
  }

  // Error handling tests
  test("parse completely invalid JSON returns error") {
    val invalidJson = "{ this is not valid json at all }"
    val result      = JsonParser.importGameContext(invalidJson)
    assert(result.isLeft)
    assert(result.left.toOption.get.message.contains("JSON parsing error"))
  }

  test("parse empty string returns error") {
    val result = JsonParser.importGameContext("")
    assert(result.isLeft)
    assert(result.left.toOption.get.message.contains("JSON parsing error"))
  }

  test("parse number value returns error") {
    val result = JsonParser.importGameContext("123")
    assert(result.isLeft)
  }

  test("parse malformed JSON object returns error") {
    val malformed = """{"metadata": {"unclosed": """
    val result    = JsonParser.importGameContext(malformed)
    assert(result.isLeft)
    assert(result.left.toOption.get.message.contains("JSON parsing error"))
  }

  test("parse invalid JSON array returns error") {
    val invalidArray = "[1, 2, 3"
    val result       = JsonParser.importGameContext(invalidArray)
    assert(result.isLeft)
  }

  test("parse JSON with missing required fields") {
    val json   = """{"metadata": {}}"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
  }

  // Edge cases with defaults
  test("parse invalid turn color returns error") {
    val json   = """{
      "metadata": {},
      "gameState": {"turn": "Invalid", "board": []},
      "moves": []
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isLeft)
    assert(result.left.toOption.get.message.contains("Invalid turn color"))
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

  // Move type parsing tests
  test("parse all move type variations") {
    val json   = """{
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
    val json   = """{
      "metadata": {"event": "Game"},
      "gameState": {"turn": "White", "board": []},
      "moves": [{"from": "e2", "to": "e4", "type": {"type": "unknown"}}]
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
  }

  test("parse promotion with invalid piece uses default") {
    val json   = """{
      "metadata": {},
      "gameState": {"turn": "White", "board": []},
      "moves": [{"from": "a7", "to": "a8", "type": {"type": "promotion", "promotionPiece": "invalid"}}]
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
  }

  test("parse move with invalid from/to skips it") {
    val json   = """{
      "metadata": {},
      "gameState": {"turn": "White", "board": []},
      "moves": [{"from": "e2", "to": "invalid", "type": {"type": "normal"}}]
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx = result.toOption.get
    assert(ctx.moves.isEmpty)
  }

  test("parse normal move with isCapture true") {
    val json   = """{
      "metadata": {},
      "gameState": {"turn": "White", "board": []},
      "moves": [{"from": "e4", "to": "d5", "type": {"type": "normal", "isCapture": true}}]
    }"""
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
    val ctx  = result.toOption.get
    val move = ctx.moves.head
    assert(move.moveType == MoveType.Normal(true))
  }

  test("parse board with invalid pieces filters them") {
    val json   = """{
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
    assert(ctx.board.pieces.size == 1)
  }

  test("parse with empty board") {
    val json   = """{
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
    val json =
      "{\"metadata\": {}, \"gameState\": {\"board\": [], \"turn\": \"White\", \"castlingRights\": {\"whiteKingSide\": true, \"whiteQueenSide\": true, \"blackKingSide\": true, \"blackQueenSide\": true}, \"enPassantSquare\": null, \"halfMoveClock\": 0}, \"moves\": [], \"moveHistory\": \"\", \"capturedPieces\": {\"byWhite\": [], \"byBlack\": []}, \"timestamp\": \"2026-01-01T00:00:00Z\"}"
    val result = JsonParser.importGameContext(json)
    assert(result.isRight)
  }
