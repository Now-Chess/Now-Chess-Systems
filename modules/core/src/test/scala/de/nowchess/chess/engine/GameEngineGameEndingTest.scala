package de.nowchess.chess.engine

import de.nowchess.rules.sets.DefaultRules
import scala.collection.mutable
import de.nowchess.api.board.Color
import de.nowchess.api.game.DrawReason
import de.nowchess.chess.observer.{CheckDetectedEvent, CheckmateEvent, DrawEvent, GameEvent, Observer}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** Tests for GameEngine check/checkmate/stalemate paths */
class GameEngineGameEndingTest extends AnyFunSuite with Matchers:

  test("GameEngine handles Checkmate (Fool's Mate)"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new EndingMockObserver()
    engine.subscribe(observer)

    // Play Fool's mate
    engine.processUserInput("f2f3")
    engine.processUserInput("e7e5")
    engine.processUserInput("g2g4")

    observer.events.clear()
    engine.processUserInput("d8h4")

    // Verify CheckmateEvent (engine also fires MoveExecutedEvent before CheckmateEvent)
    observer.events.last match
      case event: CheckmateEvent =>
        event.winner shouldBe Color.Black
      case other =>
        fail(s"Expected CheckmateEvent, but got $other")

  test("GameEngine handles check detection"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new EndingMockObserver()
    engine.subscribe(observer)

    // Play a simple check
    engine.processUserInput("e2e4")
    engine.processUserInput("e7e5")
    engine.processUserInput("f1c4")
    engine.processUserInput("g8f6")

    observer.events.clear()
    engine.processUserInput("c4f7") // Check!

    val checkEvents = observer.events.collect { case e: CheckDetectedEvent => e }
    checkEvents.size shouldBe 1
    checkEvents.head.context.turn shouldBe Color.Black // Black is now in check

  // Shortest known stalemate is 19 moves. Here is a faster one:
  // e3 a5 Qh5 Ra6 Qxa5 h5 h4 Rah6 Qxc7 f6 Qxd7+ Kf7 Qxb7 Qd3 Qxb8 Qh7 Qxc8 Kg6 Qe6
  // Wait, let's just use Sam Loyd's 10-move stalemate:
  // 1. e3 a5 2. Qh5 Ra6 3. Qxa5 h5 4. h4 Rah6 5. Qxc7 f6 6. Qxd7+ Kf7 7. Qxb7 Qd3 8. Qxb8 Qh7 9. Qxc8 Kg6 10. Qe6
  test("GameEngine handles Stalemate via 10-move known sequence"):
    val engine   = new GameEngine(ruleSet = DefaultRules)
    val observer = new EndingMockObserver()
    engine.subscribe(observer)

    val moves = List(
      "e2e3",
      "a7a5",
      "d1h5",
      "a8a6",
      "h5a5",
      "h7h5",
      "h2h4",
      "a6h6",
      "a5c7",
      "f7f6",
      "c7d7",
      "e8f7",
      "d7b7",
      "d8d3",
      "b7b8",
      "d3h7",
      "b8c8",
      "f7g6",
      "c8e6",
    )

    moves.dropRight(1).foreach(engine.processUserInput)

    observer.events.clear()
    engine.processUserInput(moves.last)

    val stalemateEvents = observer.events.collect { case DrawEvent(_, DrawReason.Stalemate) => true }
    stalemateEvents.size shouldBe 1

private class EndingMockObserver extends Observer:
  val events = mutable.ListBuffer[GameEvent]()

  override def onGameEvent(event: GameEvent): Unit =
    events += event
