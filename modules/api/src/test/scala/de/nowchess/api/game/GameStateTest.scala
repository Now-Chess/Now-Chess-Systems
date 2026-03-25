package de.nowchess.api.game

import de.nowchess.api.board.Color
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameStateTest extends AnyFunSuite with Matchers:

  test("CastlingRights.None has both flags false") {
    CastlingRights.None.kingSide  shouldBe false
    CastlingRights.None.queenSide shouldBe false
  }

  test("CastlingRights.Both has both flags true") {
    CastlingRights.Both.kingSide  shouldBe true
    CastlingRights.Both.queenSide shouldBe true
  }

  test("CastlingRights constructor sets fields") {
    val cr = CastlingRights(kingSide = true, queenSide = false)
    cr.kingSide  shouldBe true
    cr.queenSide shouldBe false
  }

  test("GameResult cases exist") {
    GameResult.WhiteWins shouldBe GameResult.WhiteWins
    GameResult.BlackWins shouldBe GameResult.BlackWins
    GameResult.Draw      shouldBe GameResult.Draw
  }

  test("GameStatus.NotStarted") {
    GameStatus.NotStarted shouldBe GameStatus.NotStarted
  }

  test("GameStatus.InProgress") {
    GameStatus.InProgress shouldBe GameStatus.InProgress
  }

  test("GameStatus.Finished carries result") {
    val status = GameStatus.Finished(GameResult.Draw)
    status shouldBe GameStatus.Finished(GameResult.Draw)
    status match
      case GameStatus.Finished(r) => r shouldBe GameResult.Draw
      case _                      => fail("expected Finished")
  }

  test("GameState.initial has standard FEN piece placement") {
    GameState.initial.piecePlacement shouldBe "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
  }

  test("GameState.initial active color is White") {
    GameState.initial.activeColor shouldBe Color.White
  }

  test("GameState.initial white has full castling rights") {
    GameState.initial.castlingWhite shouldBe CastlingRights.Both
  }

  test("GameState.initial black has full castling rights") {
    GameState.initial.castlingBlack shouldBe CastlingRights.Both
  }

  test("GameState.initial en-passant target is None") {
    GameState.initial.enPassantTarget shouldBe None
  }

  test("GameState.initial half-move clock is 0") {
    GameState.initial.halfMoveClock shouldBe 0
  }

  test("GameState.initial full-move number is 1") {
    GameState.initial.fullMoveNumber shouldBe 1
  }

  test("GameState.initial status is InProgress") {
    GameState.initial.status shouldBe GameStatus.InProgress
  }
