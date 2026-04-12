package de.nowchess.io.json

import de.nowchess.api.game.GameContext
import de.nowchess.api.board.{Board, CastlingRights, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonExporterSuite extends AnyFunSuite with Matchers:

  test("exportGameContext: exports initial position") {
    val context = GameContext.initial
    val json    = JsonExporter.exportGameContext(context)

    json should include("\"metadata\"")
    json should include("\"gameState\"")
    json should include("\"moveHistory\"")
    json should include("\"capturedPieces\"")
    json should include("\"timestamp\"")
  }

  test("exportGameContext: includes board pieces") {
    val context = GameContext.initial
    val json    = JsonExporter.exportGameContext(context)

    json should include("\"a1\"")
    json should include("\"Rook\"")
    json should include("\"White\"")
  }

  test("exportGameContext: includes turn information") {
    val context = GameContext.initial
    val json    = JsonExporter.exportGameContext(context)

    json should include("\"turn\": \"White\"")
  }

  test("exportGameContext: includes castling rights") {
    val context = GameContext.initial
    val json    = JsonExporter.exportGameContext(context)

    json should include("\"whiteKingSide\": true")
    json should include("\"whiteQueenSide\": true")
  }

  test("exportGameContext: exports with moves") {
    val move    = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4))
    val context = GameContext.initial.withMove(move)
    val json    = JsonExporter.exportGameContext(context)

    json should include("\"moves\"")
    json should include("\"from\"")
    json should include("\"to\"")
    json should include("\"e2\"")
    json should include("\"e4\"")
  }

  test("exportGameContext: valid JSON structure") {
    val context = GameContext.initial
    val json    = JsonExporter.exportGameContext(context)

    json should startWith("{")
    json should endWith("}")
    json should include("\"metadata\": {")
    json should include("\"gameState\": {")
  }

  test("exportGameContext: empty move history for initial position") {
    val context = GameContext.initial
    val json    = JsonExporter.exportGameContext(context)

    json should include("\"moves\": []")
  }

  test("exportGameContext: exports en passant square") {
    val epSquare = Some(Square(File.E, Rank.R3))
    val context  = GameContext.initial.copy(enPassantSquare = epSquare)
    val json     = JsonExporter.exportGameContext(context)

    json should include("\"enPassantSquare\": \"e3\"")
  }

  test("exportGameContext: exports null en passant square") {
    val context = GameContext.initial.copy(enPassantSquare = None)
    val json    = JsonExporter.exportGameContext(context)

    json should include("\"enPassantSquare\": null")
  }

  test("exportGameContext: exports different move destinations") {
    val move    = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4))
    val context = GameContext.initial.withMove(move)
    val json    = JsonExporter.exportGameContext(context)

    json should include("\"moves\"")
  }

  test("exportGameContext: exports empty board") {
    val emptyBoard = Board(Map.empty)
    val context    = GameContext.initial.copy(board = emptyBoard)
    val json       = JsonExporter.exportGameContext(context)

    json should include("\"board\": []")
  }

  test("exportGameContext: exports all castling rights disabled") {
    val noCastling = CastlingRights(false, false, false, false)
    val context    = GameContext.initial.withCastlingRights(noCastling)
    val json       = JsonExporter.exportGameContext(context)

    json should include("\"whiteKingSide\": false")
    json should include("\"whiteQueenSide\": false")
    json should include("\"blackKingSide\": false")
    json should include("\"blackQueenSide\": false")
  }
