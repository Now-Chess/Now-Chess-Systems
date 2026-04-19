package de.nowchess.bot

import de.nowchess.api.board.{Color, File, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.bot.bots.ClassicalBot
import de.nowchess.bot.util.{PolyglotBook, PolyglotHash}
import de.nowchess.rules.sets.DefaultRules
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.{DataOutputStream, FileOutputStream}
import java.nio.file.Files
import scala.util.Using

class PolyglotBookTest extends AnyFunSuite with Matchers:

  test("Book probe returns None for non-existent file"):
    val book = PolyglotBook("/nonexistent/path/book.bin")
    book.probe(GameContext.initial) shouldEqual None

  test("Book probe returns None when position not in book"):
    val tempFile = Files.createTempFile("test_book", ".bin")
    try
      // Write a single entry with a different key
      Using(DataOutputStream(FileOutputStream(tempFile.toFile))) { dos =>
        dos.writeLong(12345L) // some random key
        dos.writeShort(0)     // move
        dos.writeShort(100)   // weight
        dos.writeInt(0)       // learn
      }.get

      val book = PolyglotBook(tempFile.toString)
      book.probe(GameContext.initial) shouldEqual None
    finally Files.delete(tempFile)

  test("Book returns a move when position is in book"):
    val tempFile = Files.createTempFile("test_book", ".bin")
    try
      val ctx  = GameContext.initial
      val hash = PolyglotHash.hash(ctx)

      // Write an entry: e2-e4 (normal move, non-capture)
      // from_file=4, from_rank=1, to_file=4, to_rank=3, promotion=0
      val move: Short = (4 | (3 << 3) | (4 << 6) | (1 << 9)).toShort

      Using(DataOutputStream(FileOutputStream(tempFile.toFile))) { dos =>
        dos.writeLong(hash)
        dos.writeShort(move)
        dos.writeShort(100) // weight
        dos.writeInt(0)
      }.get

      val book   = PolyglotBook(tempFile.toString)
      val result = book.probe(ctx)
      result shouldNot be(None)
      result.get.from shouldEqual Square(File.E, Rank.R2)
      result.get.to shouldEqual Square(File.E, Rank.R4)
    finally Files.delete(tempFile)

  test("Weighted random sampling works"):
    val tempFile = Files.createTempFile("test_book", ".bin")
    try
      val ctx  = GameContext.initial
      val hash = PolyglotHash.hash(ctx)

      // Two moves: e2-e4 with high weight, d2-d4 with low weight
      val moveE4: Short = (4 | (3 << 3) | (4 << 6) | (1 << 9)).toShort
      val moveD4: Short = (3 | (3 << 3) | (3 << 6) | (1 << 9)).toShort

      Using(DataOutputStream(FileOutputStream(tempFile.toFile))) { dos =>
        dos.writeLong(hash)
        dos.writeShort(moveE4)
        dos.writeShort(900) // high weight
        dos.writeInt(0)

        dos.writeLong(hash)
        dos.writeShort(moveD4)
        dos.writeShort(100) // low weight
        dos.writeInt(0)
      }.get

      val book = PolyglotBook(tempFile.toString)

      // Sample multiple times; high-weight move should be picked more often
      val samples = (0 until 100).map(_ => book.probe(ctx)).flatten
      samples.length should be > 0

      val e4Count = samples.count(m => m.from == Square(File.E, Rank.R2) && m.to == Square(File.E, Rank.R4))
      val d4Count = samples.count(m => m.from == Square(File.D, Rank.R2) && m.to == Square(File.D, Rank.R4))

      // With 900:100 weight ratio, e4 should appear more frequently
      e4Count should be > d4Count
    finally Files.delete(tempFile)

  test("ClassicalBot without book falls back to search"):
    val ctx  = GameContext.initial
    val bot  = ClassicalBot(BotDifficulty.Easy) // no book
    val move = bot.nextMove(ctx)
    move shouldNot be(None)
    // The move should be legal
    val allLegalMoves = DefaultRules.allLegalMoves(ctx)
    allLegalMoves should contain(move.get)

  test("ClassicalBot with book uses book move"):
    val tempFile = Files.createTempFile("test_book", ".bin")
    try
      val ctx  = GameContext.initial
      val hash = PolyglotHash.hash(ctx)

      // e2-e4
      val moveE4: Short = (4 | (3 << 3) | (4 << 6) | (1 << 9)).toShort

      Using(DataOutputStream(FileOutputStream(tempFile.toFile))) { dos =>
        dos.writeLong(hash)
        dos.writeShort(moveE4)
        dos.writeShort(100)
        dos.writeInt(0)
      }.get

      val book        = PolyglotBook(tempFile.toString)
      val botWithBook = ClassicalBot(BotDifficulty.Easy, book = Some(book))
      val move        = botWithBook.nextMove(ctx)

      // Book should return e2-e4
      move shouldEqual Some(Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal()))
    finally Files.delete(tempFile)

  test("Promotion moves are decoded correctly"):
    val tempFile = Files.createTempFile("test_book", ".bin")
    try
      val ctx  = GameContext.initial
      val hash = PolyglotHash.hash(ctx)

      // Pawn promotion: a7-a8=Q
      // from_file=0, from_rank=6, to_file=0, to_rank=7, promotion=4 (queen)
      val promoteMove: Short = (0 | (7 << 3) | (0 << 6) | (6 << 9) | (4 << 12)).toShort

      Using(DataOutputStream(FileOutputStream(tempFile.toFile))) { dos =>
        dos.writeLong(hash)
        dos.writeShort(promoteMove)
        dos.writeShort(100)
        dos.writeInt(0)
      }.get

      val book = PolyglotBook(tempFile.toString)
      val move = book.probe(ctx)

      move shouldNot be(None)
      move.get.moveType match
        case MoveType.Promotion(piece) => piece shouldEqual PromotionPiece.Queen
        case _                         => fail("Expected promotion move")
    finally Files.delete(tempFile)
