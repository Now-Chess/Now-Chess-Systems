package de.nowchess.chess.notation

import de.nowchess.api.board.*
import de.nowchess.api.move.PromotionPiece
import de.nowchess.chess.logic.{GameHistory, HistoryMove, CastleSide}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PgnExporterTest extends AnyFunSuite with Matchers:

  test("export empty game") {
    val headers = Map("Event" -> "Test", "White" -> "A", "Black" -> "B")
    val history = GameHistory.empty
    val pgn = PgnExporter.exportGame(headers, history)

    pgn.contains("[Event \"Test\"]") shouldBe true
    pgn.contains("[White \"A\"]") shouldBe true
    pgn.contains("[Black \"B\"]") shouldBe true
  }

  test("export single move") {
    val headers = Map("Event" -> "Test", "White" -> "A", "Black" -> "B")
    val history = GameHistory()
      .addMove(HistoryMove(Square(File.E, Rank.R2), Square(File.E, Rank.R4), None))
    val pgn = PgnExporter.exportGame(headers, history)

    pgn.contains("1. e2e4") shouldBe true
  }

  test("export castling") {
    val headers = Map("Event" -> "Test")
    val history = GameHistory()
      .addMove(HistoryMove(Square(File.E, Rank.R1), Square(File.G, Rank.R1), Some(CastleSide.Kingside)))
    val pgn = PgnExporter.exportGame(headers, history)

    pgn.contains("O-O") shouldBe true
  }

  test("export game sequence") {
    val headers = Map("Event" -> "Test", "White" -> "A", "Black" -> "B", "Result" -> "1-0")
    val history = GameHistory()
      .addMove(HistoryMove(Square(File.E, Rank.R2), Square(File.E, Rank.R4), None))
      .addMove(HistoryMove(Square(File.C, Rank.R7), Square(File.C, Rank.R5), None))
      .addMove(HistoryMove(Square(File.G, Rank.R1), Square(File.F, Rank.R3), None))
    val pgn = PgnExporter.exportGame(headers, history)

    pgn.contains("1. e2e4 c7c5") shouldBe true
    pgn.contains("2. g1f3") shouldBe true
  }

  test("export game with no headers returns only move text") {
    val history = GameHistory()
      .addMove(HistoryMove(Square(File.E, Rank.R2), Square(File.E, Rank.R4), None))
    val pgn = PgnExporter.exportGame(Map.empty, history)

    pgn shouldBe "1. e2e4 *"
  }

  test("export queenside castling") {
    val headers = Map("Event" -> "Test")
    val history = GameHistory()
      .addMove(HistoryMove(Square(File.E, Rank.R1), Square(File.C, Rank.R1), Some(CastleSide.Queenside)))
    val pgn = PgnExporter.exportGame(headers, history)

    pgn.contains("O-O-O") shouldBe true
  }

  test("exportGame encodes promotion to Queen as =Q suffix") {
    val history = GameHistory()
      .addMove(HistoryMove(Square(File.E, Rank.R7), Square(File.E, Rank.R8), None, Some(PromotionPiece.Queen)))
    val pgn = PgnExporter.exportGame(Map.empty, history)
    pgn should include ("e7e8=Q")
  }

  test("exportGame encodes promotion to Rook as =R suffix") {
    val history = GameHistory()
      .addMove(HistoryMove(Square(File.E, Rank.R7), Square(File.E, Rank.R8), None, Some(PromotionPiece.Rook)))
    val pgn = PgnExporter.exportGame(Map.empty, history)
    pgn should include ("e7e8=R")
  }

  test("exportGame encodes promotion to Bishop as =B suffix") {
    val history = GameHistory()
      .addMove(HistoryMove(Square(File.E, Rank.R7), Square(File.E, Rank.R8), None, Some(PromotionPiece.Bishop)))
    val pgn = PgnExporter.exportGame(Map.empty, history)
    pgn should include ("e7e8=B")
  }

  test("exportGame encodes promotion to Knight as =N suffix") {
    val history = GameHistory()
      .addMove(HistoryMove(Square(File.E, Rank.R7), Square(File.E, Rank.R8), None, Some(PromotionPiece.Knight)))
    val pgn = PgnExporter.exportGame(Map.empty, history)
    pgn should include ("e7e8=N")
  }

  test("exportGame does not add suffix for normal moves") {
    val history = GameHistory()
      .addMove(HistoryMove(Square(File.E, Rank.R2), Square(File.E, Rank.R4), None, None))
    val pgn = PgnExporter.exportGame(Map.empty, history)
    pgn should include ("e2e4")
    pgn should not include ("=")
  }

  test("exportGame uses Result header as termination marker"):
    val history = GameHistory()
      .addMove(HistoryMove(Square(File.E, Rank.R2), Square(File.E, Rank.R4), None))
    val pgn = PgnExporter.exportGame(Map("Result" -> "1/2-1/2"), history)
    pgn should endWith("1/2-1/2")

  test("exportGame with no Result header still uses * as default"):
    val history = GameHistory()
      .addMove(HistoryMove(Square(File.E, Rank.R2), Square(File.E, Rank.R4), None))
    val pgn = PgnExporter.exportGame(Map.empty, history)
    pgn shouldBe "1. e2e4 *"
