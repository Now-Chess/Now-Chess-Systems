package de.nowchess.chess.logic

import de.nowchess.api.board.*
import de.nowchess.api.game.CastlingRights
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameContextTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)
  private def board(entries: (Square, Piece)*): Board = Board(entries.toMap)

  test("GameContext.initial has Board.initial and CastlingRights.Both for both sides"):
    GameContext.initial.board         shouldBe Board.initial
    GameContext.initial.whiteCastling shouldBe CastlingRights.Both
    GameContext.initial.blackCastling shouldBe CastlingRights.Both

  test("castlingFor returns white rights for Color.White"):
    GameContext.initial.castlingFor(Color.White) shouldBe CastlingRights.Both

  test("castlingFor returns black rights for Color.Black"):
    GameContext.initial.castlingFor(Color.Black) shouldBe CastlingRights.Both

  test("withUpdatedRights updates white castling without touching black"):
    val ctx = GameContext.initial.withUpdatedRights(Color.White, CastlingRights.None)
    ctx.whiteCastling shouldBe CastlingRights.None
    ctx.blackCastling shouldBe CastlingRights.Both

  test("withUpdatedRights updates black castling without touching white"):
    val ctx = GameContext.initial.withUpdatedRights(Color.Black, CastlingRights.None)
    ctx.blackCastling shouldBe CastlingRights.None
    ctx.whiteCastling shouldBe CastlingRights.Both

  test("withCastle: white kingside — king e1→g1, rook h1→f1"):
    val b = board(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.H, Rank.R1) -> Piece.WhiteRook
    )
    val after = b.withCastle(Color.White, CastleSide.Kingside)
    after.pieceAt(sq(File.G, Rank.R1)) shouldBe Some(Piece.WhiteKing)
    after.pieceAt(sq(File.F, Rank.R1)) shouldBe Some(Piece.WhiteRook)
    after.pieceAt(sq(File.E, Rank.R1)) shouldBe None
    after.pieceAt(sq(File.H, Rank.R1)) shouldBe None

  test("withCastle: white queenside — king e1→c1, rook a1→d1"):
    val b = board(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.A, Rank.R1) -> Piece.WhiteRook
    )
    val after = b.withCastle(Color.White, CastleSide.Queenside)
    after.pieceAt(sq(File.C, Rank.R1)) shouldBe Some(Piece.WhiteKing)
    after.pieceAt(sq(File.D, Rank.R1)) shouldBe Some(Piece.WhiteRook)
    after.pieceAt(sq(File.E, Rank.R1)) shouldBe None
    after.pieceAt(sq(File.A, Rank.R1)) shouldBe None

  test("withCastle: black kingside — king e8→g8, rook h8→f8"):
    val b = board(
      sq(File.E, Rank.R8) -> Piece.BlackKing,
      sq(File.H, Rank.R8) -> Piece.BlackRook
    )
    val after = b.withCastle(Color.Black, CastleSide.Kingside)
    after.pieceAt(sq(File.G, Rank.R8)) shouldBe Some(Piece.BlackKing)
    after.pieceAt(sq(File.F, Rank.R8)) shouldBe Some(Piece.BlackRook)
    after.pieceAt(sq(File.E, Rank.R8)) shouldBe None
    after.pieceAt(sq(File.H, Rank.R8)) shouldBe None

  test("withCastle: black queenside — king e8→c8, rook a8→d8"):
    val b = board(
      sq(File.E, Rank.R8) -> Piece.BlackKing,
      sq(File.A, Rank.R8) -> Piece.BlackRook
    )
    val after = b.withCastle(Color.Black, CastleSide.Queenside)
    after.pieceAt(sq(File.C, Rank.R8)) shouldBe Some(Piece.BlackKing)
    after.pieceAt(sq(File.D, Rank.R8)) shouldBe Some(Piece.BlackRook)
    after.pieceAt(sq(File.E, Rank.R8)) shouldBe None
    after.pieceAt(sq(File.A, Rank.R8)) shouldBe None

  test("GameContext single-arg apply defaults to CastlingRights.None for both sides"):
    val ctx = GameContext(Board.initial)
    ctx.whiteCastling shouldBe CastlingRights.None
    ctx.blackCastling shouldBe CastlingRights.None
