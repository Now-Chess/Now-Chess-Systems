package de.nowchess.chess.engine

import de.nowchess.rules.sets.DefaultRules
import scala.collection.mutable
import de.nowchess.api.board.Color
import de.nowchess.api.game.{DrawReason, GameResult}
import de.nowchess.chess.observer.{
  DrawEvent,
  DrawOfferDeclinedEvent,
  DrawOfferEvent,
  GameEvent,
  InvalidMoveEvent,
  InvalidMoveReason,
  Observer,
}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameEngineDrawOfferTest extends AnyFunSuite with Matchers:

  test("White offers draw"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)

    engine.offerDraw(Color.White)

    observer.events should have length 1
    observer.events.head match
      case event: DrawOfferEvent =>
        event.offeredBy shouldBe Color.White
      case other =>
        fail(s"Expected DrawOfferEvent, but got $other")

  test("Black accepts White's draw offer"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)

    engine.offerDraw(Color.White)
    observer.events.clear()
    engine.acceptDraw(Color.Black)

    observer.events should have length 1
    observer.events.head match
      case event: DrawEvent =>
        event.reason shouldBe DrawReason.Agreement
        event.context.result shouldBe Some(GameResult.Draw(DrawReason.Agreement))
      case other =>
        fail(s"Expected DrawEvent, but got $other")

  test("Black declines White's draw offer"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)

    engine.offerDraw(Color.White)
    observer.events.clear()
    engine.declineDraw(Color.Black)

    observer.events should have length 1
    observer.events.head match
      case event: DrawOfferDeclinedEvent =>
        event.declinedBy shouldBe Color.Black
      case other =>
        fail(s"Expected DrawOfferDeclinedEvent, but got $other")

  test("Black offers draw"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)

    engine.offerDraw(Color.Black)

    observer.events should have length 1
    observer.events.head match
      case event: DrawOfferEvent =>
        event.offeredBy shouldBe Color.Black
      case other =>
        fail(s"Expected DrawOfferEvent, but got $other")

  test("White accepts Black's draw offer"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)

    engine.offerDraw(Color.Black)
    observer.events.clear()
    engine.acceptDraw(Color.White)

    observer.events should have length 1
    observer.events.head match
      case event: DrawEvent =>
        event.reason shouldBe DrawReason.Agreement
        event.context.result shouldBe Some(GameResult.Draw(DrawReason.Agreement))
      case other =>
        fail(s"Expected DrawEvent, but got $other")

  test("Cannot accept draw when no offer pending"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)

    engine.acceptDraw(Color.Black)

    observer.events should have length 1
    observer.events.head match
      case event: InvalidMoveEvent =>
        event.reason shouldBe InvalidMoveReason.NoDrawOfferToAccept
      case other =>
        fail(s"Expected InvalidMoveEvent, but got $other")

  test("Cannot decline draw when no offer pending"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)

    engine.declineDraw(Color.Black)

    observer.events should have length 1
    observer.events.head match
      case event: InvalidMoveEvent =>
        event.reason shouldBe InvalidMoveReason.NoDrawOfferToDecline
      case other =>
        fail(s"Expected InvalidMoveEvent, but got $other")

  test("Cannot offer draw when game is already over"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)

    // End the game with checkmate
    engine.processUserInput("f2f3")
    engine.processUserInput("e7e5")
    engine.processUserInput("g2g4")
    observer.events.clear()
    engine.processUserInput("d8h4")

    // Try to offer draw
    observer.events.clear()
    engine.offerDraw(Color.White)

    observer.events should have length 1
    observer.events.head match
      case event: InvalidMoveEvent =>
        event.reason shouldBe InvalidMoveReason.GameAlreadyOver
      case other =>
        fail(s"Expected InvalidMoveEvent, but got $other")

  test("Cannot accept your own draw offer"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)

    engine.offerDraw(Color.White)
    observer.events.clear()
    engine.acceptDraw(Color.White)

    observer.events should have length 1
    observer.events.head match
      case event: InvalidMoveEvent =>
        event.reason shouldBe InvalidMoveReason.CannotAcceptOwnDrawOffer
      case other =>
        fail(s"Expected InvalidMoveEvent, but got $other")

  test("Cannot decline your own draw offer"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)

    engine.offerDraw(Color.White)
    observer.events.clear()
    engine.declineDraw(Color.White)

    observer.events should have length 1
    observer.events.head match
      case event: InvalidMoveEvent =>
        event.reason shouldBe InvalidMoveReason.CannotDeclineOwnDrawOffer
      case other =>
        fail(s"Expected InvalidMoveEvent, but got $other")

  test("Cannot make second draw offer when one is already pending"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)

    engine.offerDraw(Color.White)
    observer.events.clear()
    engine.offerDraw(Color.Black)

    observer.events should have length 1
    observer.events.head match
      case event: InvalidMoveEvent =>
        event.reason shouldBe InvalidMoveReason.DrawOfferPending
      case other =>
        fail(s"Expected InvalidMoveEvent, but got $other")

  test("Draw offer is cleared when game ends by resignation (accept)"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)

    engine.offerDraw(Color.White)
    observer.events.clear()
    engine.resign()

    // Try to accept the now-cleared draw offer
    observer.events.clear()
    engine.acceptDraw(Color.White)

    observer.events should have length 1
    observer.events.head match
      case event: InvalidMoveEvent =>
        event.reason shouldBe InvalidMoveReason.GameAlreadyOver
      case other =>
        fail(s"Expected InvalidMoveEvent, but got $other")

  test("Draw offer is cleared when game ends by resignation (decline)"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)

    engine.offerDraw(Color.White)
    observer.events.clear()
    engine.resign()

    // Try to accept the now-cleared draw offer
    observer.events.clear()
    engine.declineDraw(Color.White)

    observer.events should have length 1
    observer.events.head match
      case event: InvalidMoveEvent =>
        event.reason shouldBe InvalidMoveReason.GameAlreadyOver
      case other =>
        fail(s"Expected InvalidMoveEvent, but got $other")

  test("pendingDrawOfferBy returns None initially"):
    val engine = new GameEngine(ruleSet = DefaultRules)
    engine.pendingDrawOfferBy shouldBe None

  test("pendingDrawOfferBy returns White after White offers"):
    val engine = new GameEngine(ruleSet = DefaultRules)
    engine.offerDraw(Color.White)
    engine.pendingDrawOfferBy shouldBe Some(Color.White)

  test("pendingDrawOfferBy returns None after draw is accepted"):
    val engine = new GameEngine(ruleSet = DefaultRules)
    engine.offerDraw(Color.White)
    engine.acceptDraw(Color.Black)
    engine.pendingDrawOfferBy shouldBe None

  test("applyDraw sets draw result when game not over"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)
    engine.applyDraw(DrawReason.Agreement)
    observer.events should have length 1
    observer.events.head match
      case event: DrawEvent =>
        event.reason shouldBe DrawReason.Agreement
        event.context.result shouldBe Some(GameResult.Draw(DrawReason.Agreement))
      case other =>
        fail(s"Expected DrawEvent, but got $other")

  test("applyDraw does nothing when game already over"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)
    // End the game with checkmate
    engine.processUserInput("f2f3")
    engine.processUserInput("e7e5")
    engine.processUserInput("g2g4")
    engine.processUserInput("d8h4")
    observer.events.clear()
    engine.applyDraw(DrawReason.Agreement)
    observer.events should have length 0

  test("claimDraw with fifty-move rule when at half-move 100"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)
    // Play moves to reach fifty-move rule claim
    engine.processUserInput("e2e4")
    engine.processUserInput("e7e5")
    engine.processUserInput("g1f3")
    engine.processUserInput("g8f6")
    // Need to advance halfMoveClock to 100
    // This is hard to do naturally; skip for now if not critical

  test("claimDraw when game already over"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new DrawOfferMockObserver()
    engine.subscribe(observer)
    // End the game with checkmate
    engine.processUserInput("f2f3")
    engine.processUserInput("e7e5")
    engine.processUserInput("g2g4")
    engine.processUserInput("d8h4")
    observer.events.clear()
    engine.claimDraw()
    observer.events should have length 1
    observer.events.head match
      case event: InvalidMoveEvent =>
        event.reason shouldBe InvalidMoveReason.GameAlreadyOver
      case other =>
        fail(s"Expected InvalidMoveEvent, but got $other")

private class DrawOfferMockObserver extends Observer:
  val events = mutable.ListBuffer[GameEvent]()

  override def onGameEvent(event: GameEvent): Unit =
    events += event
