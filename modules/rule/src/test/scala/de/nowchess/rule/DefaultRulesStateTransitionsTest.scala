package de.nowchess.rule

import de.nowchess.api.board.{CastlingRights, Color, Piece, PieceType, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.io.fen.FenParser
import de.nowchess.rules.sets.DefaultRules
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DefaultRulesStateTransitionsTest extends AnyFunSuite with Matchers:

  private def contextFromFen(fen: String): GameContext =
    FenParser.parseFen(fen).fold(err => fail(err.message), identity)

  private def sq(alg: String): Square =
    Square.fromAlgebraic(alg).getOrElse(fail(s"Invalid square in test: $alg"))

  test("isCheckmate returns true for a known mate pattern"):
    val context = contextFromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")

    DefaultRules.isCheck(context) shouldBe true
    DefaultRules.isCheckmate(context) shouldBe true
    DefaultRules.allLegalMoves(context) shouldBe empty

  test("isStalemate returns true for a known stalemate pattern"):
    val context = contextFromFen("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1")

    DefaultRules.isCheck(context) shouldBe false
    DefaultRules.isStalemate(context) shouldBe true
    DefaultRules.allLegalMoves(context) shouldBe empty

  test("isInsufficientMaterial returns true for king versus king"):
    val context = contextFromFen("8/8/8/8/8/8/4k3/4K3 w - - 0 1")

    DefaultRules.isInsufficientMaterial(context) shouldBe true

  test("isInsufficientMaterial returns true for king and bishop versus king"):
    val context = contextFromFen("8/8/8/8/8/8/4k3/3BK3 w - - 0 1")

    DefaultRules.isInsufficientMaterial(context) shouldBe true

  test("isInsufficientMaterial returns true for king and knight versus king"):
    val context = contextFromFen("8/8/8/8/8/8/4k3/4KN2 w - - 0 1")

    DefaultRules.isInsufficientMaterial(context) shouldBe true

  test("isInsufficientMaterial returns false for king and rook versus king"):
    val context = contextFromFen("8/8/8/8/8/8/4k3/3RK3 w - - 0 1")

    DefaultRules.isInsufficientMaterial(context) shouldBe false

  test("isFiftyMoveRule returns true when halfMoveClock is 100"):
    val context = contextFromFen("8/8/8/8/8/8/4k3/4K3 w - - 100 1")

    DefaultRules.isFiftyMoveRule(context) shouldBe true

  test("applyMove toggles turn and records move"):
    val move = Move(sq("e2"), sq("e4"))
    val next = DefaultRules.applyMove(GameContext.initial)(move)

    next.turn shouldBe Color.Black
    next.moves.lastOption shouldBe Some(move)

  test("applyMove sets en passant square after double pawn push"):
    val move = Move(sq("e2"), sq("e4"))
    val next = DefaultRules.applyMove(GameContext.initial)(move)

    next.enPassantSquare shouldBe Some(sq("e3"))

  test("applyMove clears en passant square for non double pawn push"):
    val context = contextFromFen("4k3/8/8/8/8/8/4P3/4K3 w - d6 3 1")
    val move    = Move(sq("e2"), sq("e3"))

    val next = DefaultRules.applyMove(context)(move)

    next.enPassantSquare shouldBe None

  test("applyMove resets halfMoveClock on pawn move"):
    val context = contextFromFen("4k3/8/8/8/8/8/4P3/4K3 w - - 12 1")
    val move    = Move(sq("e2"), sq("e4"))

    val next = DefaultRules.applyMove(context)(move)

    next.halfMoveClock shouldBe 0

  test("applyMove increments halfMoveClock on quiet non pawn move"):
    val context = contextFromFen("4k3/8/8/8/8/8/8/4K1N1 w - - 7 1")
    val move    = Move(sq("g1"), sq("f3"))

    val next = DefaultRules.applyMove(context)(move)

    next.halfMoveClock shouldBe 8

  test("applyMove resets halfMoveClock on capture"):
    val context = contextFromFen("r3k3/8/8/8/8/8/8/R3K3 w Qq - 9 1")
    val move    = Move(sq("a1"), sq("a8"), MoveType.Normal(isCapture = true))

    val next = DefaultRules.applyMove(context)(move)

    next.halfMoveClock shouldBe 0
    next.board.pieceAt(sq("a8")) shouldBe Some(Piece(Color.White, PieceType.Rook))

  test("applyMove updates castling rights after king move"):
    val context = contextFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
    val move    = Move(sq("e1"), sq("e2"))

    val next = DefaultRules.applyMove(context)(move)

    next.castlingRights.whiteKingSide shouldBe false
    next.castlingRights.whiteQueenSide shouldBe false
    next.castlingRights.blackKingSide shouldBe true
    next.castlingRights.blackQueenSide shouldBe true

  test("applyMove updates castling rights after rook move from h1"):
    val context = contextFromFen("r3k2r/8/8/8/8/8/8/4K2R w KQkq - 0 1")
    val move    = Move(sq("h1"), sq("h2"))

    val next = DefaultRules.applyMove(context)(move)

    next.castlingRights.whiteKingSide shouldBe false
    next.castlingRights.whiteQueenSide shouldBe true

  test("applyMove revokes opponent castling right when rook on starting square is captured"):
    val context = contextFromFen("r3k3/8/8/8/8/8/8/R3K3 w Qq - 2 1")
    val move    = Move(sq("a1"), sq("a8"), MoveType.Normal(isCapture = true))

    val next = DefaultRules.applyMove(context)(move)

    next.castlingRights.blackQueenSide shouldBe false

  test("applyMove executes kingside castling and repositions king and rook"):
    val context = contextFromFen("4k2r/8/8/8/8/8/8/R3K2R w KQk - 0 1")
    val move    = Move(sq("e1"), sq("g1"), MoveType.CastleKingside)

    val next = DefaultRules.applyMove(context)(move)

    next.board.pieceAt(sq("g1")) shouldBe Some(Piece(Color.White, PieceType.King))
    next.board.pieceAt(sq("f1")) shouldBe Some(Piece(Color.White, PieceType.Rook))
    next.board.pieceAt(sq("e1")) shouldBe None
    next.board.pieceAt(sq("h1")) shouldBe None

  test("applyMove executes queenside castling and repositions king and rook"):
    val context = contextFromFen("r3k3/8/8/8/8/8/8/R3K2R w KQq - 0 1")
    val move    = Move(sq("e1"), sq("c1"), MoveType.CastleQueenside)

    val next = DefaultRules.applyMove(context)(move)

    next.board.pieceAt(sq("c1")) shouldBe Some(Piece(Color.White, PieceType.King))
    next.board.pieceAt(sq("d1")) shouldBe Some(Piece(Color.White, PieceType.Rook))
    next.board.pieceAt(sq("e1")) shouldBe None
    next.board.pieceAt(sq("a1")) shouldBe None

  test("applyMove executes en passant and removes captured pawn"):
    val context = contextFromFen("k7/8/8/3pP3/8/8/8/7K w - d6 0 1")
    val move    = Move(sq("e5"), sq("d6"), MoveType.EnPassant)

    val next = DefaultRules.applyMove(context)(move)

    next.board.pieceAt(sq("d6")) shouldBe Some(Piece(Color.White, PieceType.Pawn))
    next.board.pieceAt(sq("d5")) shouldBe None
    next.board.pieceAt(sq("e5")) shouldBe None

  test("applyMove executes promotion with selected piece type"):
    val context = contextFromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
    val move    = Move(sq("a7"), sq("a8"), MoveType.Promotion(PromotionPiece.Knight))

    val next = DefaultRules.applyMove(context)(move)

    next.board.pieceAt(sq("a8")) shouldBe Some(Piece(Color.White, PieceType.Knight))
    next.board.pieceAt(sq("a7")) shouldBe None

  test("candidateMoves returns empty for opponent piece on selected square"):
    val context = GameContext.initial.withTurn(Color.Black)

    DefaultRules.candidateMoves(context)(sq("e2")) shouldBe empty

  test("legalMoves keeps king safe by filtering pinned bishop moves"):
    val context = contextFromFen("8/8/8/8/8/8/r1B1K3/8 w - - 0 1")

    val bishopMoves = DefaultRules.legalMoves(context)(sq("c2"))

    bishopMoves shouldBe empty

  test("applyMove preserves black castling rights after white kingside castling"):
    val context = contextFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
    val move    = Move(sq("e1"), sq("g1"), MoveType.CastleKingside)

    val next = DefaultRules.applyMove(context)(move)

    next.castlingRights.whiteKingSide shouldBe false
    next.castlingRights.whiteQueenSide shouldBe false
    next.castlingRights.blackKingSide shouldBe true
    next.castlingRights.blackQueenSide shouldBe true

  test("applyMove can revoke both white castling rights when both rooks are captured"):
    val context = GameContext(
      board =
        contextFromFen("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1").board.updated(sq("a8"), Piece(Color.Black, PieceType.Queen)),
      turn = Color.Black,
      castlingRights = CastlingRights(true, true, false, false),
      enPassantSquare = None,
      halfMoveClock = 0,
      moves = List.empty,
    )

    val afterA1Capture = DefaultRules.applyMove(context)(Move(sq("a8"), sq("a1"), MoveType.Normal(isCapture = true)))
    val afterH1Capture =
      DefaultRules.applyMove(afterA1Capture)(Move(sq("a1"), sq("h1"), MoveType.Normal(isCapture = true)))

    afterH1Capture.castlingRights.whiteKingSide shouldBe false
    afterH1Capture.castlingRights.whiteQueenSide shouldBe false

  test("isInsufficientMaterial returns true for two same-square-color bishops (one each side)"):
    // White bishop d1 (dark), black bishop g2 (dark) — same square color → draw
    val context = contextFromFen("8/8/8/8/8/8/4k1b1/3BK3 w - - 0 1")

    DefaultRules.isInsufficientMaterial(context) shouldBe true

  test("isInsufficientMaterial returns false for two different-square-color bishops (one each side)"):
    // White bishop d1 (dark), black bishop d2 (light) — different square colors → not a draw
    val context = contextFromFen("8/8/8/4k3/8/8/3b4/3BK3 w - - 0 1")

    DefaultRules.isInsufficientMaterial(context) shouldBe false

  test("isInsufficientMaterial returns true for two same-color bishops vs lone king"):
    // White bishops on c1 (light) and e3 (light), black king only → draw
    val context = contextFromFen("4k3/8/8/8/8/4B3/8/2B1K3 w - - 0 1")

    DefaultRules.isInsufficientMaterial(context) shouldBe true

  test("isInsufficientMaterial returns false for bishop and knight versus king"):
    // K+B+N vs K is sufficient material
    val context = contextFromFen("4k3/8/8/8/8/8/8/3BKN2 w - - 0 1")

    DefaultRules.isInsufficientMaterial(context) shouldBe false

  test("candidateMoves for rook includes enemy capture move"):
    val context = contextFromFen("4k3/8/8/8/8/8/4K3/R6r w - - 0 1")

    val rookMoves = DefaultRules.candidateMoves(context)(sq("a1"))

    rookMoves.exists(m => m.to == sq("h1") && m.moveType == MoveType.Normal(isCapture = true)) shouldBe true

  test("candidateMoves for knight includes enemy capture move"):
    val context = contextFromFen("4k3/8/8/8/8/3p4/5N2/4K3 w - - 0 1")

    val knightMoves = DefaultRules.candidateMoves(context)(sq("f2"))

    knightMoves.exists(m => m.to == sq("d3") && m.moveType == MoveType.Normal(isCapture = true)) shouldBe true

  test("candidateMoves includes black kingside and queenside castling options"):
    val context = contextFromFen("r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1")

    val kingMoves = DefaultRules.candidateMoves(context)(sq("e8"))

    kingMoves.exists(_.moveType == MoveType.CastleKingside) shouldBe true
    kingMoves.exists(_.moveType == MoveType.CastleQueenside) shouldBe true

  test("applyMove executes black kingside castling and repositions pieces on rank 8"):
    val context = contextFromFen("r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1")
    val move    = Move(sq("e8"), sq("g8"), MoveType.CastleKingside)

    val next = DefaultRules.applyMove(context)(move)

    next.board.pieceAt(sq("g8")) shouldBe Some(Piece(Color.Black, PieceType.King))
    next.board.pieceAt(sq("f8")) shouldBe Some(Piece(Color.Black, PieceType.Rook))
    next.board.pieceAt(sq("e8")) shouldBe None
    next.board.pieceAt(sq("h8")) shouldBe None

  test("applyMove revokes black castling rights when black rook moves from h8"):
    val context = contextFromFen("r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1")
    val move    = Move(sq("h8"), sq("h7"))

    val next = DefaultRules.applyMove(context)(move)

    next.castlingRights.blackKingSide shouldBe false
    next.castlingRights.blackQueenSide shouldBe true

  test("applyMove revokes black queenside castling right when black rook moves from a8"):
    val context = contextFromFen("r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1")
    val move    = Move(sq("a8"), sq("a7"))

    val next = DefaultRules.applyMove(context)(move)

    next.castlingRights.blackKingSide shouldBe true
    next.castlingRights.blackQueenSide shouldBe false

  test("applyMove revokes black kingside castling right when rook on h8 is captured"):
    val context = contextFromFen("4k2r/8/8/8/8/8/8/4K2R w Kk - 0 1")
    val move    = Move(sq("h1"), sq("h8"), MoveType.Normal(isCapture = true))

    val next = DefaultRules.applyMove(context)(move)

    next.castlingRights.blackKingSide shouldBe false

  test("candidateMoves creates all promotion move variants for black pawn"):
    val context = contextFromFen("4k3/8/8/8/8/8/p7/4K3 b - - 0 1")
    val to      = sq("a1")

    val pawnMoves  = DefaultRules.candidateMoves(context)(sq("a2"))
    val promotions = pawnMoves.collect { case Move(_, `to`, MoveType.Promotion(piece)) => piece }

    promotions.toSet shouldBe Set(
      PromotionPiece.Queen,
      PromotionPiece.Rook,
      PromotionPiece.Bishop,
      PromotionPiece.Knight,
    )

  test("applyMove promotion supports queen rook and bishop targets"):
    val base = contextFromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")

    val queen  = DefaultRules.applyMove(base)(Move(sq("a7"), sq("a8"), MoveType.Promotion(PromotionPiece.Queen)))
    val rook   = DefaultRules.applyMove(base)(Move(sq("a7"), sq("a8"), MoveType.Promotion(PromotionPiece.Rook)))
    val bishop = DefaultRules.applyMove(base)(Move(sq("a7"), sq("a8"), MoveType.Promotion(PromotionPiece.Bishop)))

    queen.board.pieceAt(sq("a8")) shouldBe Some(Piece(Color.White, PieceType.Queen))
    rook.board.pieceAt(sq("a8")) shouldBe Some(Piece(Color.White, PieceType.Rook))
    bishop.board.pieceAt(sq("a8")) shouldBe Some(Piece(Color.White, PieceType.Bishop))

  test("applyMove preserves castling rights when rook moves from non-starting square"):
    val context = contextFromFen("r3k2r/8/8/8/8/8/4R3/4K3 w KQkq - 0 1")
    val move    = Move(sq("e2"), sq("e3"))

    val next = DefaultRules.applyMove(context)(move)

    next.castlingRights.whiteKingSide shouldBe true
    next.castlingRights.whiteQueenSide shouldBe true
    next.castlingRights.blackKingSide shouldBe true
    next.castlingRights.blackQueenSide shouldBe true
