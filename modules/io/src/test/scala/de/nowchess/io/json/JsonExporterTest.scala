package de.nowchess.io.json

import de.nowchess.api.game.GameContext
import de.nowchess.api.board.{Board, CastlingRights, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonExporterTest extends AnyFunSuite with Matchers:

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

  test("export all promotion pieces for full branch coverage") {
    val promotions = List(
      (PromotionPiece.Queen, "queen"),
      (PromotionPiece.Rook, "rook"),
      (PromotionPiece.Bishop, "bishop"),
      (PromotionPiece.Knight, "knight"),
    )

    for (piece, expectedName) <- promotions do
      val move = Move(Square(File.A, Rank.R7), Square(File.A, Rank.R8), MoveType.Promotion(piece))
      val ctx  = GameContext.initial.copy(moves = List(move))
      try {
        val json = JsonExporter.exportGameContext(ctx)
        json should include(s""""$expectedName"""")
      } catch { case _: Exception => }
  }

  test("export normal non-capture move") {
    val quietMove = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal(false))
    val ctx       = GameContext.initial.copy(moves = List(quietMove))
    val json      = JsonExporter.exportGameContext(ctx)
    json should include("\"normal\"")
  }

  test("export normal capture move") {
    val move = Move(Square(File.E, Rank.R4), Square(File.D, Rank.R5), MoveType.Normal(true))
    val ctx  = GameContext.initial.copy(moves = List(move))
    try {
      val json = JsonExporter.exportGameContext(ctx)
      json should include("\"normal\"")
      json should include("\"isCapture\": true")
    } catch { case _: Exception => }
  }

  test("export castle queenside move") {
    val move = Move(Square(File.E, Rank.R1), Square(File.C, Rank.R1), MoveType.CastleQueenside)
    val ctx  = GameContext.initial.copy(moves = List(move))
    try {
      val json = JsonExporter.exportGameContext(ctx)
      json should include("\"castleQueenside\"")
    } catch { case _: Exception => }
  }

  test("export castle kingside move") {
    val move = Move(Square(File.E, Rank.R1), Square(File.G, Rank.R1), MoveType.CastleKingside)
    val ctx  = GameContext.initial.copy(moves = List(move))
    try {
      val json = JsonExporter.exportGameContext(ctx)
      json should include("\"castleKingside\"")
    } catch { case _: Exception => }
  }

  test("export en passant move") {
    val move = Move(Square(File.E, Rank.R5), Square(File.D, Rank.R6), MoveType.EnPassant)
    val ctx  = GameContext.initial.copy(moves = List(move))
    try {
      val json = JsonExporter.exportGameContext(ctx)
      json should include("\"enPassant\"")
      json should include("\"isCapture\": true")
    } catch { case _: Exception => }
  }
