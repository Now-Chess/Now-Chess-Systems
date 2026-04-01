package de.nowchess.chess.controller

import de.nowchess.api.board.*
import de.nowchess.api.game.CastlingRights
import de.nowchess.api.move.PromotionPiece
import de.nowchess.chess.logic.{CastleSide, GameHistory}
import de.nowchess.chess.notation.FenParser
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameControllerTest extends AnyFunSuite with Matchers:

  private def sq(f: File, r: Rank): Square = Square(f, r)
  private def processMove(board: Board, history: GameHistory, turn: Color, raw: String): MoveResult =
    GameController.processMove(board, history, turn, raw)

  private def castlingRights(history: GameHistory, color: Color): CastlingRights =
    de.nowchess.chess.logic.CastlingRightsCalculator.deriveCastlingRights(history, color)

  // ──── processMove ────────────────────────────────────────────────────

  test("processMove: 'quit' input returns Quit"):
    processMove(Board.initial, GameHistory.empty, Color.White, "quit") shouldBe MoveResult.Quit

  test("processMove: 'q' input returns Quit"):
    processMove(Board.initial, GameHistory.empty, Color.White, "q") shouldBe MoveResult.Quit

  test("processMove: quit with surrounding whitespace returns Quit"):
    processMove(Board.initial, GameHistory.empty, Color.White, "  quit  ") shouldBe MoveResult.Quit

  test("processMove: unparseable input returns InvalidFormat"):
    processMove(Board.initial, GameHistory.empty, Color.White, "xyz") shouldBe MoveResult.InvalidFormat("xyz")

  test("processMove: valid format but empty square returns NoPiece"):
    // E3 is empty in the initial position
    processMove(Board.initial, GameHistory.empty, Color.White, "e3e4") shouldBe MoveResult.NoPiece

  test("processMove: piece of wrong color returns WrongColor"):
    // E7 has a Black pawn; it is White's turn
    processMove(Board.initial, GameHistory.empty, Color.White, "e7e6") shouldBe MoveResult.WrongColor

  test("processMove: geometrically illegal move returns IllegalMove"):
    // White pawn at E2 cannot jump three squares to E5
    processMove(Board.initial, GameHistory.empty, Color.White, "e2e5") shouldBe MoveResult.IllegalMove

  test("processMove: move that leaves own king in check returns IllegalMove"):
    // White King E1 is in check from Black Rook E8. Moving the D2 pawn is
    // geometrically legal but does not resolve the check — must be rejected.
    val b = Board(Map(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.D, Rank.R2) -> Piece.WhitePawn,
      sq(File.E, Rank.R8) -> Piece.BlackRook,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    ))
    processMove(b, GameHistory.empty, Color.White, "d2d4") shouldBe MoveResult.IllegalMove

  test("processMove: move that resolves check is allowed"):
    // White King E1 is in check from Black Rook E8 along the E-file.
    // White Rook A5 interposes at E5 — resolves the check, no new check on Black King A8.
    val b = Board(Map(
      sq(File.E, Rank.R1) -> Piece.WhiteKing,
      sq(File.A, Rank.R5) -> Piece.WhiteRook,
      sq(File.E, Rank.R8) -> Piece.BlackRook,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    ))
    processMove(b, GameHistory.empty, Color.White, "a5e5") match
      case _: MoveResult.Moved => succeed
      case other => fail(s"Expected Moved, got $other")

  test("processMove: legal pawn move returns Moved with updated board and flipped turn"):
    processMove(Board.initial, GameHistory.empty, Color.White, "e2e4") match
      case MoveResult.Moved(newBoard, newHistory, captured, newTurn) =>
        newBoard.pieceAt(sq(File.E, Rank.R4)) shouldBe Some(Piece.WhitePawn)
        newBoard.pieceAt(sq(File.E, Rank.R2)) shouldBe None
        captured shouldBe None
        newTurn shouldBe Color.Black
      case other => fail(s"Expected Moved, got $other")

  test("processMove: legal capture returns Moved with the captured piece"):
    val board = Board(Map(
      sq(File.E, Rank.R5) -> Piece.WhitePawn,
      sq(File.D, Rank.R6) -> Piece.BlackPawn,
      sq(File.H, Rank.R1) -> Piece.BlackKing,
      sq(File.H, Rank.R8) -> Piece.WhiteKing
    ))
    processMove(board, GameHistory.empty, Color.White, "e5d6") match
      case MoveResult.Moved(newBoard, newHistory, captured, newTurn) =>
        captured shouldBe Some(Piece.BlackPawn)
        newBoard.pieceAt(sq(File.D, Rank.R6)) shouldBe Some(Piece.WhitePawn)
        newTurn shouldBe Color.Black
      case other => fail(s"Expected Moved, got $other")

  // ──── processMove: check / checkmate / stalemate ─────────────────────

  test("processMove: legal move that delivers check returns MovedInCheck"):
    // White Ra1, Ka3; Black Kh8 — White plays Ra1-Ra8, Ra8 attacks rank 8 putting Kh8 in check
    // Kh8 can escape to g7/g8/h7 so this is InCheck, not Mated
    val b = Board(Map(
      sq(File.A, Rank.R1) -> Piece.WhiteRook,
      sq(File.C, Rank.R3) -> Piece.WhiteKing,
      sq(File.H, Rank.R8) -> Piece.BlackKing
    ))
    processMove(b, GameHistory.empty, Color.White, "a1a8") match
      case MoveResult.MovedInCheck(_, _, _, newTurn) => newTurn shouldBe Color.Black
      case other => fail(s"Expected MovedInCheck, got $other")

  test("processMove: legal move that results in checkmate returns Checkmate"):
    // White Qa1, Ka6; Black Ka8 — White plays Qa1-Qh8 (diagonal a1→h8)
    // After Qh8: White Qh8 + Ka6 vs Black Ka8 = checkmate (spec-verified position)
    // Qa1 does NOT currently attack Ka8 — path along file A is blocked by Ka6
    val b = Board(Map(
      sq(File.A, Rank.R1) -> Piece.WhiteQueen,
      sq(File.A, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    ))
    processMove(b, GameHistory.empty, Color.White, "a1h8") match
      case MoveResult.Checkmate(winner) => winner shouldBe Color.White
      case other => fail(s"Expected Checkmate(White), got $other")

  test("processMove: legal move that results in stalemate returns Stalemate"):
    // White Qb1, Kc6; Black Ka8 — White plays Qb1-Qb6
    // After Qb6: White Qb6 + Kc6 vs Black Ka8 = stalemate (spec-verified position)
    val b = Board(Map(
      sq(File.B, Rank.R1) -> Piece.WhiteQueen,
      sq(File.C, Rank.R6) -> Piece.WhiteKing,
      sq(File.A, Rank.R8) -> Piece.BlackKing
    ))
    processMove(b, GameHistory.empty, Color.White, "b1b6") match
      case MoveResult.Stalemate => succeed
      case other => fail(s"Expected Stalemate, got $other")

  // ──── castling execution ─────────────────────────────────────────────

  test("processMove: e1g1 returns Moved with king on g1 and rook on f1"):
    val b = Board(Map(
        sq(File.E, Rank.R1) -> Piece.WhiteKing,
        sq(File.H, Rank.R1) -> Piece.WhiteRook,
        sq(File.H, Rank.R8) -> Piece.BlackKing
      ))
    processMove(b, GameHistory.empty, Color.White, "e1g1") match
      case MoveResult.Moved(newBoard, newHistory, captured, newTurn) =>
        newBoard.pieceAt(sq(File.G, Rank.R1)) shouldBe Some(Piece.WhiteKing)
        newBoard.pieceAt(sq(File.F, Rank.R1)) shouldBe Some(Piece.WhiteRook)
        newBoard.pieceAt(sq(File.E, Rank.R1)) shouldBe None
        newBoard.pieceAt(sq(File.H, Rank.R1)) shouldBe None
        captured shouldBe None
        newTurn shouldBe Color.Black
      case other => fail(s"Expected Moved, got $other")

  test("processMove: e1c1 returns Moved with king on c1 and rook on d1"):
    val b = Board(Map(
        sq(File.E, Rank.R1) -> Piece.WhiteKing,
        sq(File.A, Rank.R1) -> Piece.WhiteRook,
        sq(File.H, Rank.R8) -> Piece.BlackKing
      ))
    processMove(b, GameHistory.empty, Color.White, "e1c1") match
      case MoveResult.Moved(newBoard, _, _, _) =>
        newBoard.pieceAt(sq(File.C, Rank.R1)) shouldBe Some(Piece.WhiteKing)
        newBoard.pieceAt(sq(File.D, Rank.R1)) shouldBe Some(Piece.WhiteRook)
      case other => fail(s"Expected Moved, got $other")

  // ──── rights revocation ──────────────────────────────────────────────

  test("processMove: e1g1 revokes both white castling rights"):
    val b = Board(Map(
        sq(File.E, Rank.R1) -> Piece.WhiteKing,
        sq(File.H, Rank.R1) -> Piece.WhiteRook,
        sq(File.H, Rank.R8) -> Piece.BlackKing
      ))
    processMove(b, GameHistory.empty, Color.White, "e1g1") match
      case MoveResult.Moved(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.White) shouldBe CastlingRights.None
      case other => fail(s"Expected Moved, got $other")

  test("processMove: moving rook from h1 revokes white kingside right"):
    val b = Board(Map(
        sq(File.E, Rank.R1) -> Piece.WhiteKing,
        sq(File.H, Rank.R1) -> Piece.WhiteRook,
        sq(File.H, Rank.R8) -> Piece.BlackKing
      ))
    processMove(b, GameHistory.empty, Color.White, "h1h4") match
      case MoveResult.Moved(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.White).kingSide  shouldBe false
        castlingRights(newHistory, Color.White).queenSide shouldBe true
      case MoveResult.MovedInCheck(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.White).kingSide  shouldBe false
        castlingRights(newHistory, Color.White).queenSide shouldBe true
      case other => fail(s"Expected Moved or MovedInCheck, got $other")

  test("processMove: moving king from e1 revokes both white rights"):
    val b = Board(Map(
        sq(File.E, Rank.R1) -> Piece.WhiteKing,
        sq(File.H, Rank.R8) -> Piece.BlackKing
      ))
    processMove(b, GameHistory.empty, Color.White, "e1e2") match
      case MoveResult.Moved(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.White) shouldBe CastlingRights.None
      case other => fail(s"Expected Moved, got $other")

  test("processMove: enemy capture on h1 revokes white kingside right"):
    val b = Board(Map(
        sq(File.E, Rank.R1) -> Piece.WhiteKing,
        sq(File.H, Rank.R1) -> Piece.WhiteRook,
        sq(File.H, Rank.R2) -> Piece.BlackRook,
        sq(File.A, Rank.R8) -> Piece.BlackKing
      ))
    processMove(b, GameHistory.empty, Color.Black, "h2h1") match
      case MoveResult.Moved(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.White).kingSide shouldBe false
      case MoveResult.MovedInCheck(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.White).kingSide shouldBe false
      case other => fail(s"Expected Moved or MovedInCheck, got $other")

  test("processMove: castle attempt when rights revoked returns IllegalMove"):
    val b = Board(Map(
        sq(File.E, Rank.R1) -> Piece.WhiteKing,
        sq(File.H, Rank.R1) -> Piece.WhiteRook,
        sq(File.H, Rank.R8) -> Piece.BlackKing
      ))
    val history = GameHistory.empty.addMove(sq(File.E, Rank.R1), sq(File.E, Rank.R2)).addMove(sq(File.E, Rank.R2), sq(File.E, Rank.R1))
    processMove(b, history, Color.White, "e1g1") shouldBe MoveResult.IllegalMove

  test("processMove: castle attempt when rook not on home square returns IllegalMove"):
    val b = Board(Map(
        sq(File.E, Rank.R1) -> Piece.WhiteKing,
        sq(File.G, Rank.R1) -> Piece.WhiteRook,
        sq(File.H, Rank.R8) -> Piece.BlackKing
      ))
    processMove(b, GameHistory.empty, Color.White, "e1g1") shouldBe MoveResult.IllegalMove

  test("processMove: moving king from e8 revokes both black rights"):
    val b = Board(Map(
        sq(File.E, Rank.R8) -> Piece.BlackKing,
        sq(File.H, Rank.R1) -> Piece.WhiteKing
      ))
    processMove(b, GameHistory.empty, Color.Black, "e8e7") match
      case MoveResult.Moved(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.Black) shouldBe CastlingRights.None
      case MoveResult.MovedInCheck(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.Black) shouldBe CastlingRights.None
      case other => fail(s"Expected Moved or MovedInCheck, got $other")

  test("processMove: moving rook from a8 revokes black queenside right"):
    val b = Board(Map(
        sq(File.E, Rank.R8) -> Piece.BlackKing,
        sq(File.A, Rank.R8) -> Piece.BlackRook,
        sq(File.H, Rank.R1) -> Piece.WhiteKing
      ))
    processMove(b, GameHistory.empty, Color.Black, "a8a1") match
      case MoveResult.Moved(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.Black).queenSide shouldBe false
        castlingRights(newHistory, Color.Black).kingSide  shouldBe true
      case MoveResult.MovedInCheck(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.Black).queenSide shouldBe false
        castlingRights(newHistory, Color.Black).kingSide  shouldBe true
      case other => fail(s"Expected Moved or MovedInCheck, got $other")

  test("processMove: moving rook from h8 revokes black kingside right"):
    val b = Board(Map(
        sq(File.E, Rank.R8) -> Piece.BlackKing,
        sq(File.H, Rank.R8) -> Piece.BlackRook,
        sq(File.A, Rank.R1) -> Piece.WhiteKing
      ))
    processMove(b, GameHistory.empty, Color.Black, "h8h4") match
      case MoveResult.Moved(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.Black).kingSide  shouldBe false
        castlingRights(newHistory, Color.Black).queenSide shouldBe true
      case MoveResult.MovedInCheck(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.Black).kingSide  shouldBe false
        castlingRights(newHistory, Color.Black).queenSide shouldBe true
      case other => fail(s"Expected Moved or MovedInCheck, got $other")

  test("processMove: enemy capture on a1 revokes white queenside right"):
    val b = Board(Map(
        sq(File.E, Rank.R1) -> Piece.WhiteKing,
        sq(File.A, Rank.R1) -> Piece.WhiteRook,
        sq(File.A, Rank.R2) -> Piece.BlackRook,
        sq(File.H, Rank.R8) -> Piece.BlackKing
      ))
    processMove(b, GameHistory.empty, Color.Black, "a2a1") match
      case MoveResult.Moved(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.White).queenSide shouldBe false
      case MoveResult.MovedInCheck(_, newHistory, _, _) =>
        castlingRights(newHistory, Color.White).queenSide shouldBe false
      case other => fail(s"Expected Moved or MovedInCheck, got $other")

  // ──── en passant ────────────────────────────────────────────────────────

  test("en passant capture removes the captured pawn from the board"):
    // Setup: white pawn e5, black pawn just double-pushed to d5 (ep target = d6)
    val b = Board(Map(
      Square(File.E, Rank.R5) -> Piece.WhitePawn,
      Square(File.D, Rank.R5) -> Piece.BlackPawn,
      Square(File.E, Rank.R1) -> Piece.WhiteKing,
      Square(File.E, Rank.R8) -> Piece.BlackKing
    ))
    val h = GameHistory.empty.addMove(Square(File.D, Rank.R7), Square(File.D, Rank.R5))
    val result = GameController.processMove(b, h, Color.White, "e5d6")
    result match
      case MoveResult.Moved(newBoard, _, captured, _) =>
        newBoard.pieceAt(Square(File.D, Rank.R5)) shouldBe None  // captured pawn removed
        newBoard.pieceAt(Square(File.D, Rank.R6)) shouldBe Some(Piece.WhitePawn)  // capturing pawn placed
        captured shouldBe Some(Piece.BlackPawn)
      case other => fail(s"Expected Moved but got $other")

  test("en passant capture by black removes the captured white pawn"):
    // Setup: black pawn d4, white pawn just double-pushed to e4 (ep target = e3)
    val b = Board(Map(
      Square(File.D, Rank.R4) -> Piece.BlackPawn,
      Square(File.E, Rank.R4) -> Piece.WhitePawn,
      Square(File.E, Rank.R8) -> Piece.BlackKing,
      Square(File.E, Rank.R1) -> Piece.WhiteKing
    ))
    val h = GameHistory.empty.addMove(Square(File.E, Rank.R2), Square(File.E, Rank.R4))
    val result = GameController.processMove(b, h, Color.Black, "d4e3")
    result match
      case MoveResult.Moved(newBoard, _, captured, _) =>
        newBoard.pieceAt(Square(File.E, Rank.R4)) shouldBe None  // captured pawn removed
        newBoard.pieceAt(Square(File.E, Rank.R3)) shouldBe Some(Piece.BlackPawn)  // capturing pawn placed
        captured shouldBe Some(Piece.WhitePawn)
      case other => fail(s"Expected Moved but got $other")

  // ──── pawn promotion detection ───────────────────────────────────────────

  test("processMove detects white pawn reaching R8 and returns PromotionRequired"):
    val board = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val result = GameController.processMove(board, GameHistory.empty, Color.White, "e7e8")
    result should matchPattern { case _: MoveResult.PromotionRequired => }
    result match
      case MoveResult.PromotionRequired(from, to, _, _, _, turn) =>
        from should be (sq(File.E, Rank.R7))
        to   should be (sq(File.E, Rank.R8))
        turn should be (Color.White)
      case _ => fail("Expected PromotionRequired")

  test("processMove detects black pawn reaching R1 and returns PromotionRequired"):
    val board = FenParser.parseBoard("8/8/8/8/4K3/8/4p3/8").get
    val result = GameController.processMove(board, GameHistory.empty, Color.Black, "e2e1")
    result should matchPattern { case _: MoveResult.PromotionRequired => }
    result match
      case MoveResult.PromotionRequired(from, to, _, _, _, turn) =>
        from should be (sq(File.E, Rank.R2))
        to   should be (sq(File.E, Rank.R1))
        turn should be (Color.Black)
      case _ => fail("Expected PromotionRequired")

  test("processMove detects pawn capturing to back rank as PromotionRequired with captured piece"):
    val board = FenParser.parseBoard("3q4/4P3/8/8/8/8/8/8").get
    val result = GameController.processMove(board, GameHistory.empty, Color.White, "e7d8")
    result should matchPattern { case _: MoveResult.PromotionRequired => }
    result match
      case MoveResult.PromotionRequired(_, _, _, _, captured, _) =>
        captured should be (Some(Piece(Color.Black, PieceType.Queen)))
      case _ => fail("Expected PromotionRequired")

  // ──── completePromotion ──────────────────────────────────────────────────

  test("completePromotion applies move and places queen"):
    // Black king on h1: not attacked by queen on e8 (different file, rank, and diagonals)
    val board = FenParser.parseBoard("8/4P3/8/8/8/8/8/7k").get
    val result = GameController.completePromotion(
      board, GameHistory.empty,
      sq(File.E, Rank.R7), sq(File.E, Rank.R8),
      PromotionPiece.Queen, Color.White
    )
    result should matchPattern { case _: MoveResult.Moved => }
    result match
      case MoveResult.Moved(newBoard, newHistory, _, _) =>
        newBoard.pieceAt(sq(File.E, Rank.R8)) should be (Some(Piece(Color.White, PieceType.Queen)))
        newBoard.pieceAt(sq(File.E, Rank.R7)) should be (None)
        newHistory.moves should have length 1
        newHistory.moves.head.promotionPiece should be (Some(PromotionPiece.Queen))
      case _ => fail("Expected Moved")

  test("completePromotion with rook underpromotion"):
    // Black king on h1: not attacked by rook on e8 (different file and rank)
    val board = FenParser.parseBoard("8/4P3/8/8/8/8/8/7k").get
    val result = GameController.completePromotion(
      board, GameHistory.empty,
      sq(File.E, Rank.R7), sq(File.E, Rank.R8),
      PromotionPiece.Rook, Color.White
    )
    result match
      case MoveResult.Moved(newBoard, newHistory, _, _) =>
        newBoard.pieceAt(sq(File.E, Rank.R8)) should be (Some(Piece(Color.White, PieceType.Rook)))
        newHistory.moves.head.promotionPiece should be (Some(PromotionPiece.Rook))
      case _ => fail("Expected Moved with Rook")

  test("completePromotion with bishop underpromotion"):
    val board = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val result = GameController.completePromotion(
      board, GameHistory.empty,
      sq(File.E, Rank.R7), sq(File.E, Rank.R8),
      PromotionPiece.Bishop, Color.White
    )
    result match
      case MoveResult.Moved(newBoard, newHistory, _, _) =>
        newBoard.pieceAt(sq(File.E, Rank.R8)) should be (Some(Piece(Color.White, PieceType.Bishop)))
        newHistory.moves.head.promotionPiece should be (Some(PromotionPiece.Bishop))
      case _ => fail("Expected Moved with Bishop")

  test("completePromotion with knight underpromotion"):
    val board = FenParser.parseBoard("8/4P3/4k3/8/8/8/8/8").get
    val result = GameController.completePromotion(
      board, GameHistory.empty,
      sq(File.E, Rank.R7), sq(File.E, Rank.R8),
      PromotionPiece.Knight, Color.White
    )
    result match
      case MoveResult.Moved(newBoard, newHistory, _, _) =>
        newBoard.pieceAt(sq(File.E, Rank.R8)) should be (Some(Piece(Color.White, PieceType.Knight)))
        newHistory.moves.head.promotionPiece should be (Some(PromotionPiece.Knight))
      case _ => fail("Expected Moved with Knight")

  test("completePromotion captures opponent piece"):
    // Black king on h1: after white queen captures d8 queen, h1 king is safe (queen on d8 does not attack h1)
    val board = FenParser.parseBoard("3q4/4P3/8/8/8/8/8/7k").get
    val result = GameController.completePromotion(
      board, GameHistory.empty,
      sq(File.E, Rank.R7), sq(File.D, Rank.R8),
      PromotionPiece.Queen, Color.White
    )
    result match
      case MoveResult.Moved(newBoard, _, captured, _) =>
        newBoard.pieceAt(sq(File.D, Rank.R8)) should be (Some(Piece(Color.White, PieceType.Queen)))
        captured should be (Some(Piece(Color.Black, PieceType.Queen)))
      case _ => fail("Expected Moved with captured piece")

  test("completePromotion for black pawn to R1"):
    val board = FenParser.parseBoard("8/8/8/8/4K3/8/4p3/8").get
    val result = GameController.completePromotion(
      board, GameHistory.empty,
      sq(File.E, Rank.R2), sq(File.E, Rank.R1),
      PromotionPiece.Knight, Color.Black
    )
    result match
      case MoveResult.Moved(newBoard, newHistory, _, _) =>
        newBoard.pieceAt(sq(File.E, Rank.R1)) should be (Some(Piece(Color.Black, PieceType.Knight)))
        newHistory.moves.head.promotionPiece should be (Some(PromotionPiece.Knight))
      case _ => fail("Expected Moved")

  test("completePromotion evaluates check after promotion"):
    val board = FenParser.parseBoard("3k4/4P3/8/8/8/8/8/8").get
    val result = GameController.completePromotion(
      board, GameHistory.empty,
      sq(File.E, Rank.R7), sq(File.E, Rank.R8),
      PromotionPiece.Queen, Color.White
    )
    result should matchPattern { case _: MoveResult.MovedInCheck => }

  test("completePromotion full round-trip via processMove then completePromotion"):
    // Black king on h1: not attacked by queen on e8
    val board = FenParser.parseBoard("8/4P3/8/8/8/8/8/7k").get
    GameController.processMove(board, GameHistory.empty, Color.White, "e7e8") match
      case MoveResult.PromotionRequired(from, to, boardBefore, histBefore, _, turn) =>
        val result = GameController.completePromotion(boardBefore, histBefore, from, to, PromotionPiece.Queen, turn)
        result should matchPattern { case _: MoveResult.Moved => }
        result match
          case MoveResult.Moved(finalBoard, finalHistory, _, _) =>
            finalBoard.pieceAt(sq(File.E, Rank.R8)) should be (Some(Piece(Color.White, PieceType.Queen)))
            finalHistory.moves.head.promotionPiece should be (Some(PromotionPiece.Queen))
          case _ => fail("Expected Moved")
      case _ => fail("Expected PromotionRequired")

  test("completePromotion results in checkmate when promotion delivers checkmate"):
    // Black king a8, white pawn h7, white king b6.
    // After h7→h8=Q: Qh8 attacks rank 8 putting Ka8 in check;
    // a7 covered by Kb6, b7 covered by Kb6, b8 covered by Qh8 — no escape.
    val board = FenParser.parseBoard("k7/7P/1K6/8/8/8/8/8").get
    val result = GameController.completePromotion(
      board, GameHistory.empty,
      sq(File.H, Rank.R7), sq(File.H, Rank.R8),
      PromotionPiece.Queen, Color.White
    )
    result should matchPattern { case MoveResult.Checkmate(_) => }
    result match
      case MoveResult.Checkmate(winner) => winner should be (Color.White)
      case _ => fail("Expected Checkmate")

  test("completePromotion results in stalemate when promotion stalemates opponent"):
    // Black king a8, white pawn b7, white bishop c7, white king b6.
    // After b7→b8=N: knight on b8 (doesn't check a8); a7 and b7 covered by Kb6;
    // b8 defended by Bc7 so Ka8xb8 would walk into bishop — no legal moves.
    val board = FenParser.parseBoard("k7/1PB5/1K6/8/8/8/8/8").get
    val result = GameController.completePromotion(
      board, GameHistory.empty,
      sq(File.B, Rank.R7), sq(File.B, Rank.R8),
      PromotionPiece.Knight, Color.White
    )
    result should be (MoveResult.Stalemate)
