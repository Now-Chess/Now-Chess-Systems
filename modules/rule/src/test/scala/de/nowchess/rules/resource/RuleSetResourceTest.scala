package de.nowchess.rules.resource

import com.fasterxml.jackson.databind.ObjectMapper
import de.nowchess.api.board.{Board, CastlingRights, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.rules.config.JacksonConfig
import de.nowchess.rules.dto.{ContextMoveRequest, ContextSquareRequest}
import de.nowchess.rules.sets.DefaultRules
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test

@QuarkusTest
class RuleSetResourceTest:

  private val mapper: ObjectMapper =
    val m = new ObjectMapper()
    new JacksonConfig().customize(m)
    m

  private val rules = DefaultRules

  private def request() = RestAssured.`given`()

  private def toJson(value: AnyRef): String = mapper.writeValueAsString(value)

  private def contextSquareBody(ctx: GameContext, square: String): String =
    toJson(ContextSquareRequest(ctx, square))

  private def contextMoveBody(ctx: GameContext, move: Move): String =
    toJson(ContextMoveRequest(ctx, move))

  // ── all-legal-moves ───────────────────────────────────────────────

  @Test
  def allLegalMoves_initialPositionHas20Moves(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(GameContext.initial))
      .when()
      .post("/api/rules/all-legal-moves")
      .`then`()
      .statusCode(200)
      .body("size()", is(20))

  // ── legal-moves ───────────────────────────────────────────────────

  @Test
  def legalMoves_e2PawnHas2Moves(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(contextSquareBody(GameContext.initial, "e2"))
      .when()
      .post("/api/rules/legal-moves")
      .`then`()
      .statusCode(200)
      .body("size()", is(2))

  // ── candidate-moves ───────────────────────────────────────────────

  @Test
  def candidateMoves_e2PawnHas2Candidates(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(contextSquareBody(GameContext.initial, "e2"))
      .when()
      .post("/api/rules/candidate-moves")
      .`then`()
      .statusCode(200)
      .body("size()", is(2))

  // ── is-check ──────────────────────────────────────────────────────

  @Test
  def isCheck_falseForInitialPosition(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(GameContext.initial))
      .when()
      .post("/api/rules/is-check")
      .`then`()
      .statusCode(200)
      .body(is("false"))

  @Test
  def isCheck_trueWhenKingAttacked(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(buildCheckContext()))
      .when()
      .post("/api/rules/is-check")
      .`then`()
      .statusCode(200)
      .body(is("true"))

  // ── is-checkmate ──────────────────────────────────────────────────

  @Test
  def isCheckmate_falseForInitialPosition(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(GameContext.initial))
      .when()
      .post("/api/rules/is-checkmate")
      .`then`()
      .statusCode(200)
      .body(is("false"))

  @Test
  def isCheckmate_trueForFoolsMate(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(buildFoolsMate()))
      .when()
      .post("/api/rules/is-checkmate")
      .`then`()
      .statusCode(200)
      .body(is("true"))

  // ── is-stalemate ──────────────────────────────────────────────────

  @Test
  def isStalemate_falseForInitialPosition(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(GameContext.initial))
      .when()
      .post("/api/rules/is-stalemate")
      .`then`()
      .statusCode(200)
      .body(is("false"))

  @Test
  def isStalemate_trueForStalematePosition(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(buildStalemateContext()))
      .when()
      .post("/api/rules/is-stalemate")
      .`then`()
      .statusCode(200)
      .body(is("true"))

  // ── is-insufficient-material ──────────────────────────────────────

  @Test
  def isInsufficientMaterial_falseForInitialPosition(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(GameContext.initial))
      .when()
      .post("/api/rules/is-insufficient-material")
      .`then`()
      .statusCode(200)
      .body(is("false"))

  @Test
  def isInsufficientMaterial_trueForKingsOnly(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(buildKingsOnlyContext()))
      .when()
      .post("/api/rules/is-insufficient-material")
      .`then`()
      .statusCode(200)
      .body(is("true"))

  // ── is-fifty-move-rule ────────────────────────────────────────────

  @Test
  def isFiftyMoveRule_falseForInitialPosition(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(GameContext.initial))
      .when()
      .post("/api/rules/is-fifty-move-rule")
      .`then`()
      .statusCode(200)
      .body(is("false"))

  @Test
  def isFiftyMoveRule_trueWhenClockAt100(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(GameContext.initial.copy(halfMoveClock = 100)))
      .when()
      .post("/api/rules/is-fifty-move-rule")
      .`then`()
      .statusCode(200)
      .body(is("true"))

  // ── is-threefold-repetition ───────────────────────────────────────

  @Test
  def isThreefoldRepetition_falseForInitialPosition(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(GameContext.initial))
      .when()
      .post("/api/rules/is-threefold-repetition")
      .`then`()
      .statusCode(200)
      .body(is("false"))

  @Test
  def isThreefoldRepetition_trueAfterRepeatedMoves(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(buildThreefoldContext()))
      .when()
      .post("/api/rules/is-threefold-repetition")
      .`then`()
      .statusCode(200)
      .body(is("true"))

  // ── apply-move ────────────────────────────────────────────────────

  @Test
  def applyMove_updatesContext(): Unit =
    val move = rules
      .legalMoves(GameContext.initial)(Square(File.E, Rank.R2))
      .find(_.to == Square(File.E, Rank.R4))
      .get
    request()
      .contentType(ContentType.JSON)
      .body(contextMoveBody(GameContext.initial, move))
      .when()
      .post("/api/rules/apply-move")
      .`then`()
      .statusCode(200)
      .body("turn", is("Black"))

  // ── error handling ────────────────────────────────────────────────

  @Test
  def invalidSquare_returns400(): Unit =
    request()
      .contentType(ContentType.JSON)
      .body(toJson(ContextSquareRequest(GameContext.initial, "z9")))
      .when()
      .post("/api/rules/legal-moves")
      .`then`()
      .statusCode(400)

  // ── position builders ─────────────────────────────────────────────

  private def buildCheckContext(): GameContext =
    val board = Board(
      Map(
        Square(File.E, Rank.R1) -> Piece(Color.White, PieceType.King),
        Square(File.E, Rank.R8) -> Piece(Color.Black, PieceType.King),
        Square(File.E, Rank.R3) -> Piece(Color.Black, PieceType.Rook),
      ),
    )
    GameContext(board, Color.White, CastlingRights.None, None, 0, List.empty, initialBoard = board)

  private def buildFoolsMate(): GameContext =
    val moves = List(("f2", "f3"), ("e7", "e5"), ("g2", "g4"), ("d8", "h4"))
    moves.foldLeft(GameContext.initial) { (ctx, fromTo) =>
      val from = Square.fromAlgebraic(fromTo._1).get
      val to   = Square.fromAlgebraic(fromTo._2).get
      rules.legalMoves(ctx)(from).find(_.to == to).fold(ctx)(rules.applyMove(ctx))
    }

  private def buildStalemateContext(): GameContext =
    val board = Board(
      Map(
        Square(File.H, Rank.R8) -> Piece(Color.Black, PieceType.King),
        Square(File.F, Rank.R7) -> Piece(Color.White, PieceType.Queen),
        Square(File.G, Rank.R6) -> Piece(Color.White, PieceType.King),
      ),
    )
    GameContext(board, Color.Black, CastlingRights.None, None, 0, List.empty, initialBoard = board)

  private def buildKingsOnlyContext(): GameContext =
    val board = Board(
      Map(
        Square(File.E, Rank.R1) -> Piece(Color.White, PieceType.King),
        Square(File.E, Rank.R8) -> Piece(Color.Black, PieceType.King),
      ),
    )
    GameContext(board, Color.White, CastlingRights.None, None, 0, List.empty, initialBoard = board)

  private def buildThreefoldContext(): GameContext =
    val g1 = Square(File.G, Rank.R1)
    val f3 = Square(File.F, Rank.R3)
    val g8 = Square(File.G, Rank.R8)
    val f6 = Square(File.F, Rank.R6)
    def mv(ctx: GameContext, from: Square, to: Square): GameContext =
      rules.legalMoves(ctx)(from).find(_.to == to).fold(ctx)(rules.applyMove(ctx))
    val ctx1 = mv(GameContext.initial, g1, f3)
    val ctx2 = mv(ctx1, g8, f6)
    val ctx3 = mv(ctx2, f3, g1)
    val ctx4 = mv(ctx3, f6, g8)
    val ctx5 = mv(ctx4, g1, f3)
    val ctx6 = mv(ctx5, g8, f6)
    val ctx7 = mv(ctx6, f3, g1)
    mv(ctx7, f6, g8)
