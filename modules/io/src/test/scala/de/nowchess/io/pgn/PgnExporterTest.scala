package de.nowchess.io.pgn

import de.nowchess.api.board.*
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PgnExporterTest extends AnyFunSuite with Matchers:

  test("exportGame renders headers and basic move text"):
    val headers  = Map("Event" -> "Test", "White" -> "A", "Black" -> "B")
    val emptyPgn = PgnExporter.exportGame(headers, List.empty)
    emptyPgn.contains("[Event \"Test\"]") shouldBe true
    emptyPgn.contains("[White \"A\"]") shouldBe true
    emptyPgn.contains("[Black \"B\"]") shouldBe true

    val moves = List(Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal()))
    PgnExporter.exportGame(headers, moves).contains("1. e4") shouldBe true

  test("exportGame renders castling grouping and result markers"):
    PgnExporter.exportGame(
      Map("Event" -> "Test"),
      List(Move(Square(File.E, Rank.R1), Square(File.G, Rank.R1), MoveType.CastleKingside)),
    ) should include("O-O")
    PgnExporter.exportGame(
      Map("Event" -> "Test"),
      List(Move(Square(File.E, Rank.R1), Square(File.C, Rank.R1), MoveType.CastleQueenside)),
    ) should include("O-O-O")

    val seq = List(
      Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal()),
      Move(Square(File.C, Rank.R7), Square(File.C, Rank.R5), MoveType.Normal()),
      Move(Square(File.G, Rank.R1), Square(File.F, Rank.R3), MoveType.Normal()),
    )
    val grouped = PgnExporter.exportGame(Map("Result" -> "1-0"), seq)
    grouped should include("1. e4 c5")
    grouped should include("2. Nf3")

    val oneMove = List(Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal()))
    PgnExporter.exportGame(Map.empty, oneMove) shouldBe "1. e4 *"
    PgnExporter.exportGame(Map("Result" -> "1/2-1/2"), oneMove) should endWith("1/2-1/2")

  test("exportGame handles promotion suffixes and normal move formatting"):
    List(
      PromotionPiece.Queen  -> "=Q",
      PromotionPiece.Rook   -> "=R",
      PromotionPiece.Bishop -> "=B",
      PromotionPiece.Knight -> "=N",
    ).foreach { (piece, suffix) =>
      val move = Move(Square(File.E, Rank.R7), Square(File.E, Rank.R8), MoveType.Promotion(piece))
      PgnExporter.exportGame(Map.empty, List(move)) should include(s"e8$suffix")
    }

    val normal =
      PgnExporter.exportGame(Map.empty, List(Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal())))
    normal should include("e4")
    normal should not include "="

  test("exportGameContext preserves moves and default headers"):
    val moves = List(
      Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal()),
      Move(Square(File.E, Rank.R7), Square(File.E, Rank.R5), MoveType.Normal()),
    )
    val withMoves = PgnExporter.exportGameContext(GameContext.initial.copy(moves = moves))
    withMoves.contains("e4") shouldBe true
    withMoves.contains("e5") shouldBe true

    val empty = PgnExporter.exportGameContext(GameContext.initial)
    empty.contains("[Event") shouldBe true
    empty.contains("*") shouldBe true

  private def sq(alg: String): Square =
    Square.fromAlgebraic(alg).getOrElse(fail(s"Invalid square in test: $alg"))

  test("exportGame emits notation for all normal piece types and captures"):
    val moves = List(
      Move(sq("e2"), sq("e4")),
      Move(sq("a7"), sq("a6")),
      Move(sq("g1"), sq("f3")),
      Move(sq("b7"), sq("b6")),
      Move(sq("f1"), sq("b5"), MoveType.Normal(true)),
      Move(sq("g8"), sq("f6")),
      Move(sq("a1"), sq("a8"), MoveType.Normal(true)),
      Move(sq("c7"), sq("c6")),
      Move(sq("d1"), sq("d7"), MoveType.Normal(true)),
      Move(sq("d8"), sq("d7"), MoveType.Normal(true)),
      Move(sq("e1"), sq("e2"), MoveType.Normal(true)),
    )

    val pgn = PgnExporter.exportGame(Map("Result" -> "*"), moves)

    pgn should include("e4")
    pgn should include("Nf3")
    pgn should include("Bxb5")
    pgn should include("Rxa8")
    pgn should include("Qxd7")
    pgn should include("Kxe2")

  test("exportGame emits en-passant and promotion capture notation"):
    val enPassant           = Move(sq("e2"), sq("d3"), MoveType.EnPassant)
    val promotionCapture    = Move(sq("e7"), sq("f8"), MoveType.Promotion(PromotionPiece.Queen))
    val pawnCapture         = Move(sq("e2"), sq("d3"), MoveType.Normal(isCapture = true))
    val promotionQuietSetup = Move(sq("e8"), sq("e7"))
    val promotionQuiet      = Move(sq("e2"), sq("e8"), MoveType.Promotion(PromotionPiece.Queen))

    val pgn               = PgnExporter.exportGame(Map.empty, List(enPassant, promotionCapture))
    val pawnCapturePgn    = PgnExporter.exportGame(Map.empty, List(pawnCapture))
    val quietPromotionPgn = PgnExporter.exportGame(Map.empty, List(promotionQuietSetup, promotionQuiet))

    pgn should include("exd3")
    pgn should include("exf8=Q")
    pawnCapturePgn should include("exd3")
    quietPromotionPgn should include("e8=Q")

  test("exportGame disambiguates when two knights can reach same square"):
    // 1.Nf3 a6 2.d3 a5 3.Nfd2: d3 clears d2 so both Nb1 and Nf3 can reach d2; must emit "Nfd2"
    val moves = List(
      Move(sq("g1"), sq("f3")),
      Move(sq("a7"), sq("a6")),
      Move(sq("d2"), sq("d3")),
      Move(sq("a6"), sq("a5")),
      Move(sq("f3"), sq("d2")),
    )
    val pgn = PgnExporter.exportGame(Map.empty, moves)
    pgn should include("Nfd2")

  test("exportGame appends + after move that gives check"):
    // 1.e4 e5 2.Qh5 Nc6 3.Qxf7+ — queen captures f7, gives check to black king on e8
    val moves = List(
      Move(sq("e2"), sq("e4")),
      Move(sq("e7"), sq("e5")),
      Move(sq("d1"), sq("h5")),
      Move(sq("b8"), sq("c6")),
      Move(sq("h5"), sq("f7"), MoveType.Normal(isCapture = true)),
    )
    val pgn = PgnExporter.exportGame(Map.empty, moves)
    pgn should include("Qxf7+")

  test("exportGame appends # after checkmate move"):
    // Fool's mate: 1.f3 e5 2.g4 Qh4#
    val moves = List(
      Move(sq("f2"), sq("f3")),
      Move(sq("e7"), sq("e5")),
      Move(sq("g2"), sq("g4")),
      Move(sq("d8"), sq("h4")),
    )
    val pgn = PgnExporter.exportGame(Map("Result" -> "*"), moves)
    pgn should include("Qh4#")
