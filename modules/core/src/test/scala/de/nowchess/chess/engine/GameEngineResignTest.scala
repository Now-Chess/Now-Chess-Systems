package de.nowchess.chess.engine

import scala.collection.mutable
import de.nowchess.api.board.Color
import de.nowchess.api.game.GameResult
import de.nowchess.chess.observer.{GameEvent, InvalidMoveEvent, InvalidMoveReason, Observer, ResignEvent}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameEngineResignTest extends AnyFunSuite with Matchers:

  test("White resigns"):
    val engine   = new GameEngine()
    val observer = new ResignMockObserver()
    engine.subscribe(observer)

    engine.resign(Color.White)

    observer.events should have length 1
    observer.events.head match
      case event: ResignEvent =>
        event.resignedColor shouldBe Color.White
        event.context.result shouldBe Some(GameResult.Win(Color.Black))
      case other =>
        fail(s"Expected ResignEvent, but got $other")

  test("Black resigns"):
    val engine   = new GameEngine()
    val observer = new ResignMockObserver()
    engine.subscribe(observer)

    engine.resign(Color.Black)

    observer.events should have length 1
    observer.events.head match
      case event: ResignEvent =>
        event.resignedColor shouldBe Color.Black
        event.context.result shouldBe Some(GameResult.Win(Color.White))
      case other =>
        fail(s"Expected ResignEvent, but got $other")

  test("Cannot resign when game is already over"):
    val engine   = new GameEngine()
    val observer = new ResignMockObserver()
    engine.subscribe(observer)

    // End the game with checkmate
    engine.processUserInput("f2f3")
    engine.processUserInput("e7e5")
    engine.processUserInput("g2g4")
    observer.events.clear()
    engine.processUserInput("d8h4")

    // Try to resign
    observer.events.clear()
    engine.resign(Color.White)

    // Should get InvalidMoveEvent with GameAlreadyOver reason
    observer.events.length shouldBe 1
    observer.events.head match
      case event: InvalidMoveEvent =>
        event.reason shouldBe InvalidMoveReason.GameAlreadyOver
      case other =>
        fail(s"Expected InvalidMoveEvent, but got $other")

  test("resign() without color resigns side to move"):
    val engine   = new GameEngine()
    val observer = new ResignMockObserver()
    engine.subscribe(observer)

    engine.resign()

    engine.context.result shouldBe Some(GameResult.Win(Color.Black))

  test("resign() without color does nothing when game already over"):
    val engine   = new GameEngine()
    val observer = new ResignMockObserver()
    engine.subscribe(observer)

    // End the game with checkmate
    engine.processUserInput("f2f3")
    engine.processUserInput("e7e5")
    engine.processUserInput("g2g4")
    observer.events.clear()
    engine.processUserInput("d8h4")

    // Try to resign without color parameter
    val resultBefore = engine.context.result
    engine.resign()
    resultBefore shouldBe engine.context.result

private class ResignMockObserver extends Observer:
  val events = mutable.ListBuffer[GameEvent]()

  override def onGameEvent(event: GameEvent): Unit =
    events += event
