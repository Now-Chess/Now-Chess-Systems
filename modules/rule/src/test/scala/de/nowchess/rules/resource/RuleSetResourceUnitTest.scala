package de.nowchess.rules.resource

import de.nowchess.api.board.{Board, CastlingRights, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.rules.dto.{ContextMoveRequest, ContextSquareRequest}
import de.nowchess.rules.sets.DefaultRules
import jakarta.ws.rs.BadRequestException
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RuleSetResourceUnitTest extends AnyFunSuite with Matchers:

  private val resource = new RuleSetResource()
  private val rules    = DefaultRules

  private def ctxSq(g: GameContext, sq: String) = ContextSquareRequest(g, sq)
  private def ctxMv(g: GameContext, m: Move)    = ContextMoveRequest(g, m)

  // ── position builders ─────────────────────────────────────────────

  private def checkContext(): GameContext =
    val board = Board(
      Map(
        Square(File.E, Rank.R1) -> Piece(Color.White, PieceType.King),
        Square(File.E, Rank.R8) -> Piece(Color.Black, PieceType.King),
        Square(File.E, Rank.R3) -> Piece(Color.Black, PieceType.Rook),
      ),
    )
    GameContext(board, Color.White, CastlingRights.None, None, 0, List.empty, initialBoard = board)

  private def foolsMate(): GameContext =
    val moves = List(("f2", "f3"), ("e7", "e5"), ("g2", "g4"), ("d8", "h4"))
    moves.foldLeft(GameContext.initial) { (c, ft) =>
      val from = Square.fromAlgebraic(ft._1).get
      val to   = Square.fromAlgebraic(ft._2).get
      rules.legalMoves(c)(from).find(_.to == to).fold(c)(rules.applyMove(c))
    }

  private def stalemateContext(): GameContext =
    val board = Board(
      Map(
        Square(File.H, Rank.R8) -> Piece(Color.Black, PieceType.King),
        Square(File.F, Rank.R7) -> Piece(Color.White, PieceType.Queen),
        Square(File.G, Rank.R6) -> Piece(Color.White, PieceType.King),
      ),
    )
    GameContext(board, Color.Black, CastlingRights.None, None, 0, List.empty, initialBoard = board)

  private def kingsOnlyContext(): GameContext =
    val board = Board(
      Map(
        Square(File.E, Rank.R1) -> Piece(Color.White, PieceType.King),
        Square(File.E, Rank.R8) -> Piece(Color.Black, PieceType.King),
      ),
    )
    GameContext(board, Color.White, CastlingRights.None, None, 0, List.empty, initialBoard = board)

  private def threefoldContext(): GameContext =
    val g1 = Square(File.G, Rank.R1)
    val f3 = Square(File.F, Rank.R3)
    val g8 = Square(File.G, Rank.R8)
    val f6 = Square(File.F, Rank.R6)
    def mv(c: GameContext, from: Square, to: Square): GameContext =
      rules.legalMoves(c)(from).find(_.to == to).fold(c)(rules.applyMove(c))
    val c1 = mv(GameContext.initial, g1, f3)
    val c2 = mv(c1, g8, f6)
    val c3 = mv(c2, f3, g1)
    val c4 = mv(c3, f6, g8)
    val c5 = mv(c4, g1, f3)
    val c6 = mv(c5, g8, f6)
    val c7 = mv(c6, f3, g1)
    mv(c7, f6, g8)

  // ── allLegalMoves ─────────────────────────────────────────────────

  test("allLegalMoves returns 20 moves for initial position"):
    resource.allLegalMoves(GameContext.initial) should have size 20

  // ── legalMoves ────────────────────────────────────────────────────

  test("legalMoves returns 2 moves for e2 pawn"):
    resource.legalMoves(ctxSq(GameContext.initial, "e2")) should have size 2

  test("legalMoves throws BadRequestException for invalid square"):
    an[BadRequestException] should be thrownBy
      resource.legalMoves(ctxSq(GameContext.initial, "z9"))

  // ── candidateMoves ────────────────────────────────────────────────

  test("candidateMoves returns moves for e2 pawn"):
    resource.candidateMoves(ctxSq(GameContext.initial, "e2")) should not be empty

  test("candidateMoves throws BadRequestException for invalid square"):
    an[BadRequestException] should be thrownBy
      resource.candidateMoves(ctxSq(GameContext.initial, "z9"))

  // ── isCheck ───────────────────────────────────────────────────────

  test("isCheck returns false for initial position"):
    resource.isCheck(GameContext.initial) shouldBe false

  test("isCheck returns true when king is attacked"):
    resource.isCheck(checkContext()) shouldBe true

  // ── isCheckmate ───────────────────────────────────────────────────

  test("isCheckmate returns false for initial position"):
    resource.isCheckmate(GameContext.initial) shouldBe false

  test("isCheckmate returns true for Fool's mate"):
    resource.isCheckmate(foolsMate()) shouldBe true

  // ── isStalemate ───────────────────────────────────────────────────

  test("isStalemate returns false for initial position"):
    resource.isStalemate(GameContext.initial) shouldBe false

  test("isStalemate returns true for stalemate position"):
    resource.isStalemate(stalemateContext()) shouldBe true

  // ── isInsufficientMaterial ────────────────────────────────────────

  test("isInsufficientMaterial returns false for initial position"):
    resource.isInsufficientMaterial(GameContext.initial) shouldBe false

  test("isInsufficientMaterial returns true for kings only"):
    resource.isInsufficientMaterial(kingsOnlyContext()) shouldBe true

  // ── isFiftyMoveRule ───────────────────────────────────────────────

  test("isFiftyMoveRule returns false for initial position"):
    resource.isFiftyMoveRule(GameContext.initial) shouldBe false

  test("isFiftyMoveRule returns true when halfMoveClock is 100"):
    resource.isFiftyMoveRule(GameContext.initial.copy(halfMoveClock = 100)) shouldBe true

  // ── isThreefoldRepetition ─────────────────────────────────────────

  test("isThreefoldRepetition returns false for initial position"):
    resource.isThreefoldRepetition(GameContext.initial) shouldBe false

  test("isThreefoldRepetition returns true after repeated moves"):
    resource.isThreefoldRepetition(threefoldContext()) shouldBe true

  // ── applyMove ─────────────────────────────────────────────────────

  test("applyMove returns updated context with switched turn"):
    val move = rules
      .legalMoves(GameContext.initial)(Square(File.E, Rank.R2))
      .find(_.to == Square(File.E, Rank.R4))
      .get
    resource.applyMove(ctxMv(GameContext.initial, move)).turn shouldBe Color.Black
