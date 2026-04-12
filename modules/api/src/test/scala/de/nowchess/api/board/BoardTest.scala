package de.nowchess.api.board

import de.nowchess.api.move.Move
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BoardTest extends AnyFunSuite with Matchers:

  private val e2 = Square(File.E, Rank.R2)
  private val e4 = Square(File.E, Rank.R4)

  test("pieceAt resolves occupied and empty squares") {
    Board.initial.pieceAt(e2) shouldBe Some(Piece.WhitePawn)
    Board.initial.pieceAt(e4) shouldBe None
  }

  test("withMove moves piece and vacates origin") {
    val (board, captured) = Board.initial.withMove(e2, e4)
    captured shouldBe None
    board.pieceAt(e4) shouldBe Some(Piece.WhitePawn)
    board.pieceAt(e2) shouldBe None
  }

  test("withMove returns captured piece when destination is occupied") {
    val from              = Square(File.A, Rank.R1)
    val to                = Square(File.A, Rank.R8)
    val b                 = Board(Map(from -> Piece.WhiteRook, to -> Piece.BlackRook))
    val (board, captured) = b.withMove(from, to)
    captured shouldBe Some(Piece.BlackRook)
    board.pieceAt(to) shouldBe Some(Piece.WhiteRook)
    board.pieceAt(from) shouldBe None
  }

  test("Board.apply and pieces expose the wrapped map") {
    val map = Map(e2 -> Piece.WhitePawn)
    val b   = Board(map)
    b.pieceAt(e2) shouldBe Some(Piece.WhitePawn)
    b.pieces shouldBe map
  }

  test("initial board has expected material and pawn placement") {
    Board.initial.pieces should have size 32
    Board.initial.pieces.values.count(_.color == Color.White) shouldBe 16
    Board.initial.pieces.values.count(_.color == Color.Black) shouldBe 16

    File.values.foreach { file =>
      Board.initial.pieceAt(Square(file, Rank.R2)) shouldBe Some(Piece.WhitePawn)
      Board.initial.pieceAt(Square(file, Rank.R7)) shouldBe Some(Piece.BlackPawn)
    }
  }

  test("initial board white back rank") {
    val expectedBackRank = Vector(
      PieceType.Rook,
      PieceType.Knight,
      PieceType.Bishop,
      PieceType.Queen,
      PieceType.King,
      PieceType.Bishop,
      PieceType.Knight,
      PieceType.Rook,
    )
    File.values.zipWithIndex.foreach { (file, i) =>
      Board.initial.pieceAt(Square(file, Rank.R1)) shouldBe
        Some(Piece(Color.White, expectedBackRank(i)))
    }
  }

  test("initial board black back rank") {
    val expectedBackRank = Vector(
      PieceType.Rook,
      PieceType.Knight,
      PieceType.Bishop,
      PieceType.Queen,
      PieceType.King,
      PieceType.Bishop,
      PieceType.Knight,
      PieceType.Rook,
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
    do Board.initial.pieceAt(Square(file, rank)) shouldBe None
  }

  test("updated adds and replaces piece at squares") {
    val b     = Board(Map(e2 -> Piece.WhitePawn))
    val added = b.updated(e4, Piece.WhiteKnight)
    added.pieceAt(e2) shouldBe Some(Piece.WhitePawn)
    added.pieceAt(e4) shouldBe Some(Piece.WhiteKnight)

    val replaced = b.updated(e2, Piece.WhiteKnight)
    replaced.pieceAt(e2) shouldBe Some(Piece.WhiteKnight)
  }

  test("removed deletes piece from board") {
    val b       = Board(Map(e2 -> Piece.WhitePawn, e4 -> Piece.WhiteKnight))
    val removed = b.removed(e2)
    removed.pieceAt(e2) shouldBe None
    removed.pieceAt(e4) shouldBe Some(Piece.WhiteKnight)
  }

  test("applyMove uses move.from and move.to to relocate a piece") {
    val b = Board(Map(e2 -> Piece.WhitePawn))

    val moved = b.applyMove(Move(e2, e4))

    moved.pieceAt(e4) shouldBe Some(Piece.WhitePawn)
    moved.pieceAt(e2) shouldBe None
  }
