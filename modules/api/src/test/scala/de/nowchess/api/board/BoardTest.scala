package de.nowchess.api.board

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BoardTest extends AnyFunSuite with Matchers:

  private val e2 = Square(File.E, Rank.R2)
  private val e4 = Square(File.E, Rank.R4)
  private val d7 = Square(File.D, Rank.R7)

  test("pieceAt returns Some for occupied square") {
    Board.initial.pieceAt(e2) shouldBe Some(Piece.WhitePawn)
  }

  test("pieceAt returns None for empty square") {
    Board.initial.pieceAt(e4) shouldBe None
  }

  test("withMove moves piece and vacates origin") {
    val (board, captured) = Board.initial.withMove(e2, e4)
    captured shouldBe None
    board.pieceAt(e4) shouldBe Some(Piece.WhitePawn)
    board.pieceAt(e2) shouldBe None
  }

  test("withMove returns captured piece when destination is occupied") {
    val from = Square(File.A, Rank.R1)
    val to   = Square(File.A, Rank.R8)
    val b    = Board(Map(from -> Piece.WhiteRook, to -> Piece.BlackRook))
    val (board, captured) = b.withMove(from, to)
    captured shouldBe Some(Piece.BlackRook)
    board.pieceAt(to) shouldBe Some(Piece.WhiteRook)
    board.pieceAt(from) shouldBe None
  }

  test("pieces returns the underlying map") {
    val map = Map(e2 -> Piece.WhitePawn)
    val b   = Board(map)
    b.pieces shouldBe map
  }

  test("Board.apply constructs board from map") {
    val map = Map(e2 -> Piece.WhitePawn)
    val b   = Board(map)
    b.pieceAt(e2) shouldBe Some(Piece.WhitePawn)
  }

  test("initial board has 32 pieces") {
    Board.initial.pieces should have size 32
  }

  test("initial board has 16 white pieces") {
    Board.initial.pieces.values.count(_.color == Color.White) shouldBe 16
  }

  test("initial board has 16 black pieces") {
    Board.initial.pieces.values.count(_.color == Color.Black) shouldBe 16
  }

  test("initial board white pawns on rank 2") {
    File.values.foreach { file =>
      Board.initial.pieceAt(Square(file, Rank.R2)) shouldBe Some(Piece.WhitePawn)
    }
  }

  test("initial board black pawns on rank 7") {
    File.values.foreach { file =>
      Board.initial.pieceAt(Square(file, Rank.R7)) shouldBe Some(Piece.BlackPawn)
    }
  }

  test("initial board white back rank") {
    val expectedBackRank = Vector(
      PieceType.Rook, PieceType.Knight, PieceType.Bishop, PieceType.Queen,
      PieceType.King, PieceType.Bishop, PieceType.Knight, PieceType.Rook
    )
    File.values.zipWithIndex.foreach { (file, i) =>
      Board.initial.pieceAt(Square(file, Rank.R1)) shouldBe
        Some(Piece(Color.White, expectedBackRank(i)))
    }
  }

  test("initial board black back rank") {
    val expectedBackRank = Vector(
      PieceType.Rook, PieceType.Knight, PieceType.Bishop, PieceType.Queen,
      PieceType.King, PieceType.Bishop, PieceType.Knight, PieceType.Rook
    )
    File.values.zipWithIndex.foreach { (file, i) =>
      Board.initial.pieceAt(Square(file, Rank.R8)) shouldBe
        Some(Piece(Color.Black, expectedBackRank(i)))
    }
  }

  test("ranks 3-6 are empty on initial board") {
    val emptyRanks = Seq(Rank.R3, Rank.R4, Rank.R5, Rank.R6)
    for
      rank <- emptyRanks
      file <- File.values
    do
      Board.initial.pieceAt(Square(file, rank)) shouldBe None
  }
