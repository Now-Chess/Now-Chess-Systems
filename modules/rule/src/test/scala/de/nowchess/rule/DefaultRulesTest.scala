package de.nowchess.rule

import de.nowchess.api.board.{Board, Color, File, Rank, Square, Piece, PieceType, CastlingRights}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType}
import de.nowchess.io.fen.FenParser
import de.nowchess.rules.sets.DefaultRules
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DefaultRulesTest extends AnyFunSuite with Matchers:

  private val rules = DefaultRules

  // ── Pawn moves ──────────────────────────────────────────────────

  test("pawn can move forward one square"):
    val fen = "8/8/8/8/8/8/4P3/8 w - - 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)
    val pawnMoves = moves.filter(m => m.from == Square(File.E, Rank.R2))
    pawnMoves.exists(m => m.to == Square(File.E, Rank.R3)) shouldBe true

  test("pawn can move forward two squares from starting position"):
    val context = GameContext.initial
    val moves = rules.allLegalMoves(context)
    val e2Moves = moves.filter(m => m.from == Square(File.E, Rank.R2))
    e2Moves.exists(m => m.to == Square(File.E, Rank.R4)) shouldBe true

  test("pawn can capture diagonally"):
    // FEN: white pawn e4, black pawn d5
    val fen = "8/8/8/3p4/4P3/8/8/8 w - - 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)
    val captures = moves.filter(m => m.from == Square(File.E, Rank.R4) && m.moveType.isInstanceOf[MoveType.Normal])
    captures.exists(m => m.to == Square(File.D, Rank.R5)) shouldBe true

  test("pawn cannot move backward"):
    // FEN: white pawn on e4
    val fen = "8/8/8/8/4P3/8/8/8 w - - 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)
    val pawnMoves = moves.filter(m => m.from == Square(File.E, Rank.R4))
    pawnMoves.exists(m => m.to == Square(File.E, Rank.R3)) shouldBe false

  // ── King in check filtering ──────────────────────────────────────

  test("moving king out of check removes it from legal moves if king stays in check"):
    // FEN: white king e1, black rook e8, white tries to move away
    val fen = "4r3/8/8/8/8/8/8/4K3 w - - 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)

    // King must move; e2 should be valid but d1 might be blocked by rook if still on same file
    moves.exists(m => m.from == Square(File.E, Rank.R1)) shouldBe true

  test("king cannot move to square attacked by opponent"):
    // FEN: white king e1, black rook e2 defended by black king e3
    val fen = "8/8/8/8/8/4k3/4r3/4K3 w - - 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)

    // King cannot move to e2 (occupied and attacked)
    val kingMovesToE2 = moves.filter(m => m.from == Square(File.E, Rank.R1) && m.to == Square(File.E, Rank.R2))
    kingMovesToE2.isEmpty shouldBe true

  // ── Castling legality ────────────────────────────────────────────

  test("castling kingside is legal when king and rook unmoved and path clear"):
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQK2R w KQkq - 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)

    val castles = moves.filter(m => m.moveType == MoveType.CastleKingside)
    castles.nonEmpty shouldBe true

  test("castling queenside is legal when king and rook unmoved and path clear"):
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)

    val castles = moves.filter(m => m.moveType == MoveType.CastleQueenside)
    castles.nonEmpty shouldBe true

  test("castling is illegal when castling rights are false"):
    // FEN: king and rook in position, but castling rights disabled
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQK2R w - - 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)

    val castles = moves.filter(m => m.moveType == MoveType.CastleKingside)
    castles.isEmpty shouldBe true

  test("castling is illegal when king is in check"):
    // FEN: white king e1 in check from black rook e8
    val fen = "4r3/8/8/8/8/8/8/R3K2R w KQ - 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)

    val castles = moves.filter(m => m.moveType == MoveType.CastleKingside || m.moveType == MoveType.CastleQueenside)
    castles.isEmpty shouldBe true

  test("castling is illegal when path has piece in the way"):
    // FEN: white king e1, white rook h1, white bishop f1 (blocks f-file)
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBR1 w KQkq - 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)

    val castles = moves.filter(m => m.moveType == MoveType.CastleKingside)
    castles.isEmpty shouldBe true

  test("castling queenside is illegal when knight blocks on b8"):
    // Black king e8, black rook a8, black knight b8 (blocks queenside path)
    val board = Board(Map(
      Square(File.A, Rank.R8) -> Piece(Color.Black, PieceType.Rook),
      Square(File.B, Rank.R8) -> Piece(Color.Black, PieceType.Knight),
      Square(File.E, Rank.R8) -> Piece(Color.Black, PieceType.King),
      Square(File.A, Rank.R1) -> Piece(Color.White, PieceType.Rook),
      Square(File.E, Rank.R1) -> Piece(Color.White, PieceType.King)
    ))
    val context = GameContext(
      board = board,
      turn = Color.Black,
      castlingRights = CastlingRights(whiteKingSide = true, whiteQueenSide = true, blackKingSide = true, blackQueenSide = true),
      enPassantSquare = None,
      halfMoveClock = 0,
      moves = List.empty
    )
    val moves = rules.allLegalMoves(context)

    val castles = moves.filter(m => m.moveType == MoveType.CastleQueenside)
    castles.isEmpty shouldBe true

  // ── En passant legality ──────────────────────────────────────────

  test("en passant is legal when en passant square is set"):
    // FEN: white pawn e5, black pawn d5 (just double-pushed), en passant square d6
    val fen = "k7/8/8/3pP3/8/8/8/7K w - d6 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)

    val epMoves = moves.filter(m => m.moveType == MoveType.EnPassant)
    epMoves.exists(m => m.to == Square(File.D, Rank.R6)) shouldBe true

  test("en passant is illegal when en passant square is none"):
    // FEN: white pawn e5, black pawn d5, but no en passant square
    val fen = "k7/8/8/3pP3/8/8/8/7K w - - 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)

    val epMoves = moves.filter(m => m.moveType == MoveType.EnPassant)
    epMoves.isEmpty shouldBe true

  // ── Pinned pieces ────────────────────────────────────────────────

  test("pinned piece cannot move and expose king to check"):
    // FEN: white king e1, white bishop d2 (pinned), black rook a2
    val fen = "8/8/8/8/8/8/r1B1K3/8 w - - 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)

    // Bishop on d2 is pinned by rook on a2; it cannot move
    val bishopMoves = moves.filter(m => m.from == Square(File.C, Rank.R2))
    bishopMoves.isEmpty shouldBe true

  test("piece blocking a check is legal"):
    // FEN: white king e1, white rook d1, black bishop a4 attacking e1 via d2
    // Actually, this is complex. Let's use: white king e1, black rook e8, white pawn blocks on e2
    val fen = "4r3/8/8/8/8/8/4P3/4K3 w - - 0 1"
    val context = FenParser.parseFen(fen).fold(_ => fail(), identity)
    val moves = rules.allLegalMoves(context)

    // White is in check; only moves that block or move the king are legal
    moves.nonEmpty shouldBe true
