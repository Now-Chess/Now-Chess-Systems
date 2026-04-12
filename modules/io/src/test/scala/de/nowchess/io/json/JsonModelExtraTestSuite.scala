package de.nowchess.io.json

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonModelExtraTestSuite extends AnyFunSuite with Matchers:

  test("JsonMetadata with all fields") {
    val meta = JsonMetadata(Some("Event"), Some(Map("a" -> "b")), Some("2026-04-08"), Some("1-0"))
    assert(meta.event.contains("Event"))
    assert(meta.players.exists(_.contains("a")))
  }

  test("JsonMetadata with None fields") {
    val meta = JsonMetadata()
    assert(meta.event.isEmpty)
    assert(meta.players.isEmpty)
  }

  test("JsonPiece with square and piece") {
    val piece = JsonPiece(Some("e4"), Some("White"), Some("Pawn"))
    assert(piece.square.contains("e4"))
    assert(piece.color.contains("White"))
  }

  test("JsonCastlingRights all true") {
    val cr = JsonCastlingRights(Some(true), Some(true), Some(true), Some(true))
    assert(cr.whiteKingSide.contains(true))
    assert(cr.blackQueenSide.contains(true))
  }

  test("JsonCastlingRights all false") {
    val cr = JsonCastlingRights(Some(false), Some(false), Some(false), Some(false))
    assert(cr.whiteKingSide.contains(false))
  }

  test("JsonGameState with all fields") {
    val gs = JsonGameState(
      Some(Nil),
      Some("White"),
      Some(JsonCastlingRights()),
      Some("e3"),
      Some(5)
    )
    assert(gs.board.contains(Nil))
    assert(gs.halfMoveClock.contains(5))
  }

  test("JsonGameState with None fields") {
    val gs = JsonGameState()
    assert(gs.board.isEmpty)
    assert(gs.halfMoveClock.isEmpty)
  }

  test("JsonCapturedPieces with pieces") {
    val cp = JsonCapturedPieces(Some(List("Pawn")), Some(List("Knight")))
    assert(cp.byWhite.exists(_.contains("Pawn")))
    assert(cp.byBlack.exists(_.contains("Knight")))
  }

  test("JsonMoveType normal with capture") {
    val mt = JsonMoveType(Some("normal"), Some(true), None)
    assert(mt.`type`.contains("normal"))
    assert(mt.isCapture.contains(true))
  }

  test("JsonMoveType promotion") {
    val mt = JsonMoveType(Some("promotion"), None, Some("queen"))
    assert(mt.`type`.contains("promotion"))
    assert(mt.promotionPiece.contains("queen"))
  }

  test("JsonMoveType castle kingside") {
    val mt = JsonMoveType(Some("castleKingside"), None, None)
    assert(mt.`type`.contains("castleKingside"))
  }

  test("JsonMove with coordinates") {
    val move = JsonMove(Some("e2"), Some("e4"), Some(JsonMoveType(Some("normal"), Some(false), None)))
    assert(move.from.contains("e2"))
    assert(move.to.contains("e4"))
  }

  test("JsonGameRecord full structure") {
    val record = JsonGameRecord(
      Some(JsonMetadata()),
      Some(JsonGameState()),
      Some(""),
      Some(Nil),
      Some(JsonCapturedPieces()),
      Some("2026-04-08T00:00:00Z")
    )
    assert(record.metadata.nonEmpty)
    assert(record.timestamp.nonEmpty)
  }

  test("JsonGameRecord empty") {
    val record = JsonGameRecord()
    assert(record.metadata.isEmpty)
    assert(record.moves.isEmpty)
  }

  test("JsonPiece with no fields") {
    val piece = JsonPiece()
    assert(piece.square.isEmpty)
    assert(piece.color.isEmpty)
    assert(piece.piece.isEmpty)
  }

  test("JsonMoveType with no fields") {
    val mt = JsonMoveType()
    assert(mt.`type`.isEmpty)
    assert(mt.isCapture.isEmpty)
    assert(mt.promotionPiece.isEmpty)
  }

  test("JsonMove with empty fields") {
    val move = JsonMove()
    assert(move.from.isEmpty)
    assert(move.to.isEmpty)
    assert(move.`type`.isEmpty)
  }
