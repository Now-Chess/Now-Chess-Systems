package de.nowchess.bot

import de.nowchess.api.board.{Color, File, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.bot.util.PolyglotHash
import de.nowchess.io.fen.FenParser
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PolyglotHashTest extends AnyFunSuite with Matchers:

  test("Initial position matches Polyglot reference key"):
    val ctx = GameContext.initial
    PolyglotHash.hash(ctx) shouldEqual java.lang.Long.parseUnsignedLong("463b96181691fc9c", 16)

  test("Known Polyglot FEN vector matches reference key"):
    val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    val ctx = FenParser.parseFen(fen).toOption.getOrElse(fail("FEN parse failed"))
    PolyglotHash.hash(ctx) shouldEqual java.lang.Long.parseUnsignedLong("823c9b50fd114196", 16)

  test("Hash changes when turn changes"):
    val ctx          = GameContext.initial
    val hash1        = PolyglotHash.hash(ctx)
    val ctxBlackTurn = ctx.withTurn(Color.Black)
    val hash2        = PolyglotHash.hash(ctxBlackTurn)
    hash1 should not equal hash2

  test("Hash changes when castling rights change"):
    val ctx   = GameContext.initial
    val hash1 = PolyglotHash.hash(ctx)
    val noCastling = ctx.withCastlingRights(
      de.nowchess.api.board.CastlingRights(false, false, false, false),
    )
    val hash2 = PolyglotHash.hash(noCastling)
    hash1 should not equal hash2

  test("En passant file is ignored when no side-to-move pawn can capture"):
    val fenWithEp    = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq e3 0 1"
    val fenWithoutEp = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1"
    val withEp       = FenParser.parseFen(fenWithEp).toOption.getOrElse(fail("FEN parse failed"))
    val withoutEp    = FenParser.parseFen(fenWithoutEp).toOption.getOrElse(fail("FEN parse failed"))
    PolyglotHash.hash(withEp) shouldEqual PolyglotHash.hash(withoutEp)

  test("Different en passant files produce different hashes when capture is possible"):
    val ctx     = GameContext.initial
    val epFileE = ctx.withEnPassantSquare(Some(Square(File.E, Rank.R3)))
    val epFileD = ctx.withEnPassantSquare(Some(Square(File.D, Rank.R3)))
    val hash1   = PolyglotHash.hash(epFileE)
    val hash2   = PolyglotHash.hash(epFileD)
    hash1 should not equal hash2

  test("Removing en passant changes hash"):
    val ctx    = GameContext.initial
    val withEP = ctx.withEnPassantSquare(Some(Square(File.E, Rank.R3)))
    val hash1  = PolyglotHash.hash(withEP)
    val noEP   = withEP.withEnPassantSquare(None)
    val hash2  = PolyglotHash.hash(noEP)
    hash1 should not equal hash2
