package de.nowchess.chess.engine

import scala.collection.mutable
import de.nowchess.api.board.{Board, Color}
import de.nowchess.chess.logic.GameHistory
import de.nowchess.chess.observer.{Observer, GameEvent, MoveExecutedEvent, CheckDetectedEvent, BoardResetEvent, InvalidMoveEvent, FiftyMoveRuleAvailableEvent, DrawClaimedEvent, MoveUndoneEvent, MoveRedoneEvent}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GameEngineTest extends AnyFunSuite with Matchers:

  test("GameEngine starts with initial board state"):
    val engine = new GameEngine()
    engine.board shouldBe Board.initial
    engine.history shouldBe GameHistory.empty
    engine.turn shouldBe Color.White

  test("GameEngine accepts Observer subscription"):
    val engine = new GameEngine()
    val mockObserver = new MockObserver()
    engine.subscribe(mockObserver)
    engine.observerCount shouldBe 1

  test("GameEngine notifies observers on valid move"):
    val engine = new GameEngine()
    val mockObserver = new MockObserver()
    engine.subscribe(mockObserver)
    engine.processUserInput("e2e4")
    mockObserver.events.size shouldBe 1
    mockObserver.events.head shouldBe a[MoveExecutedEvent]

  test("GameEngine updates state after valid move"):
    val engine = new GameEngine()
    val initialTurn = engine.turn
    engine.processUserInput("e2e4")
    engine.turn shouldNot be(initialTurn)
    engine.turn shouldBe Color.Black

  test("GameEngine notifies observers on invalid move"):
    val engine = new GameEngine()
    val mockObserver = new MockObserver()
    engine.subscribe(mockObserver)
    engine.processUserInput("invalid_move")
    mockObserver.events.size shouldBe 1

  test("GameEngine notifies multiple observers"):
    val engine = new GameEngine()
    val observer1 = new MockObserver()
    val observer2 = new MockObserver()
    engine.subscribe(observer1)
    engine.subscribe(observer2)
    engine.processUserInput("e2e4")
    observer1.events.size shouldBe 1
    observer2.events.size shouldBe 1

  test("GameEngine allows observer unsubscription"):
    val engine = new GameEngine()
    val mockObserver = new MockObserver()
    engine.subscribe(mockObserver)
    engine.unsubscribe(mockObserver)
    engine.observerCount shouldBe 0

  test("GameEngine unsubscribed observer receives no events"):
    val engine = new GameEngine()
    val mockObserver = new MockObserver()
    engine.subscribe(mockObserver)
    engine.unsubscribe(mockObserver)
    engine.processUserInput("e2e4")
    mockObserver.events.size shouldBe 0

  test("GameEngine reset notifies observers and resets state"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    val observer = new MockObserver()
    engine.subscribe(observer)
    engine.reset()
    engine.board shouldBe Board.initial
    engine.turn shouldBe Color.White
    observer.events.size shouldBe 1

  test("GameEngine processes sequence of moves"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    engine.processUserInput("e2e4")
    engine.processUserInput("e7e5")
    observer.events.size shouldBe 2
    engine.turn shouldBe Color.White

  test("GameEngine is thread-safe for synchronized operations"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    val t = new Thread(() => engine.processUserInput("e2e4"))
    t.start()
    t.join()
    observer.events.size shouldBe 1

  test("GameEngine canUndo returns false initially"):
    val engine = new GameEngine()
    engine.canUndo shouldBe false

  test("GameEngine canUndo returns true after move"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    engine.canUndo shouldBe true

  test("GameEngine canRedo returns false initially"):
    val engine = new GameEngine()
    engine.canRedo shouldBe false

  test("GameEngine undo restores previous state"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    val boardAfterMove = engine.board
    engine.undo()
    engine.board shouldBe Board.initial
    engine.turn shouldBe Color.White

  test("GameEngine undo notifies observers"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    val observer = new MockObserver()
    engine.subscribe(observer)
    observer.events.clear()
    engine.undo()
    observer.events.size shouldBe 1
    observer.events.head shouldBe a[MoveUndoneEvent]

  test("GameEngine redo replays undone move"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    val boardAfterMove = engine.board
    engine.undo()
    engine.redo()
    engine.board shouldBe boardAfterMove
    engine.turn shouldBe Color.Black

  test("GameEngine canUndo false when nothing to undo"):
    val engine = new GameEngine()
    engine.canUndo shouldBe false
    engine.processUserInput("e2e4")
    engine.undo()
    engine.canUndo shouldBe false

  test("GameEngine canRedo true after undo"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    engine.undo()
    engine.canRedo shouldBe true

  test("GameEngine canRedo false after redo"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    engine.undo()
    engine.redo()
    engine.canRedo shouldBe false

  test("GameEngine undo on empty history sends invalid event"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    engine.undo()
    observer.events.size shouldBe 1
    observer.events.head shouldBe a[InvalidMoveEvent]

  test("GameEngine redo on empty redo sends invalid event"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    engine.redo()
    observer.events.size shouldBe 1
    observer.events.head shouldBe a[InvalidMoveEvent]

  test("GameEngine undo via processUserInput"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    val boardAfterMove = engine.board
    engine.processUserInput("undo")
    engine.board shouldBe Board.initial

  test("GameEngine redo via processUserInput"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    val boardAfterMove = engine.board
    engine.processUserInput("undo")
    engine.processUserInput("redo")
    engine.board shouldBe boardAfterMove

  test("GameEngine handles empty input"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    engine.processUserInput("")
    observer.events.size shouldBe 1
    observer.events.head shouldBe a[InvalidMoveEvent]

  test("GameEngine multiple undo/redo sequence"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    engine.processUserInput("e7e5")
    engine.processUserInput("g1f3")
    
    engine.turn shouldBe Color.Black
    
    engine.undo()
    engine.turn shouldBe Color.White
    
    engine.undo()
    engine.turn shouldBe Color.Black
    
    engine.undo()
    engine.turn shouldBe Color.White
    engine.board shouldBe Board.initial

  test("GameEngine redo after multiple undos"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    engine.processUserInput("e7e5")
    engine.processUserInput("g1f3")
    
    engine.undo()
    engine.undo()
    engine.undo()
    
    engine.redo()
    engine.turn shouldBe Color.Black
    
    engine.redo()
    engine.turn shouldBe Color.White
    
    engine.redo()
    engine.turn shouldBe Color.Black

  test("GameEngine new move after undo clears redo history"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    engine.processUserInput("e7e5")
    engine.undo()
    engine.canRedo shouldBe true
    
    engine.processUserInput("e7e6")  // Different move
    engine.canRedo shouldBe false

  test("GameEngine command history tracking"):
    val engine = new GameEngine()
    engine.commandHistory.size shouldBe 0
    
    engine.processUserInput("e2e4")
    engine.commandHistory.size shouldBe 1
    
    engine.processUserInput("e7e5")
    engine.commandHistory.size shouldBe 2

  test("GameEngine quit input"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    val initialEvents = observer.events.size
    engine.processUserInput("quit")
    // quit should not produce an event
    observer.events.size shouldBe initialEvents

  test("GameEngine quit via q"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    val initialEvents = observer.events.size
    engine.processUserInput("q")
    observer.events.size shouldBe initialEvents
  
  test("GameEngine undo notifies with MoveUndoneEvent after successful undo"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    engine.processUserInput("e7e5")
    val observer = new MockObserver()
    engine.subscribe(observer)
    observer.events.clear()

    engine.undo()

    // Should have received a MoveUndoneEvent on undo
    observer.events.size should be > 0
    observer.events.exists(_.isInstanceOf[MoveUndoneEvent]) shouldBe true

  test("GameEngine redo notifies with MoveRedoneEvent after successful redo"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    engine.processUserInput("e7e5")
    val boardAfterSecondMove = engine.board

    engine.undo()
    val observer = new MockObserver()
    engine.subscribe(observer)
    observer.events.clear()

    engine.redo()

    // Should have received a MoveRedoneEvent for the redo
    observer.events.size shouldBe 1
    observer.events.head shouldBe a[MoveRedoneEvent]
    engine.board shouldBe boardAfterSecondMove
    engine.turn shouldBe Color.White

  // ──── 50-move rule ───────────────────────────────────────────────────

  test("GameEngine: 'draw' rejected when halfMoveClock < 100"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    engine.processUserInput("draw")
    observer.events.size shouldBe 1
    observer.events.head shouldBe a[InvalidMoveEvent]

  test("GameEngine: 'draw' accepted and fires DrawClaimedEvent when halfMoveClock >= 100"):
    val engine = new GameEngine(initialHistory = GameHistory(halfMoveClock = 100))
    val observer = new MockObserver()
    engine.subscribe(observer)
    engine.processUserInput("draw")
    observer.events.size shouldBe 1
    observer.events.head shouldBe a[DrawClaimedEvent]

  test("GameEngine: state resets to initial after draw claimed"):
    val engine = new GameEngine(initialHistory = GameHistory(halfMoveClock = 100))
    engine.processUserInput("draw")
    engine.board shouldBe Board.initial
    engine.history shouldBe GameHistory.empty
    engine.turn shouldBe Color.White

  test("GameEngine: FiftyMoveRuleAvailableEvent fired when move brings clock to 100"):
    // Start at clock 99; a knight move (non-pawn, non-capture) increments to 100
    val engine = new GameEngine(initialHistory = GameHistory(halfMoveClock = 99))
    val observer = new MockObserver()
    engine.subscribe(observer)
    engine.processUserInput("g1f3")  // knight move on initial board
    // Should receive MoveExecutedEvent AND FiftyMoveRuleAvailableEvent
    observer.events.exists(_.isInstanceOf[FiftyMoveRuleAvailableEvent]) shouldBe true

  test("GameEngine: FiftyMoveRuleAvailableEvent not fired when clock is below 100 after move"):
    val engine = new GameEngine(initialHistory = GameHistory(halfMoveClock = 5))
    val observer = new MockObserver()
    engine.subscribe(observer)
    engine.processUserInput("g1f3")
    observer.events.exists(_.isInstanceOf[FiftyMoveRuleAvailableEvent]) shouldBe false

  // Mock Observer for testing
  private class MockObserver extends Observer:
    val events = mutable.ListBuffer[GameEvent]()
    override def onGameEvent(event: GameEvent): Unit =
      events += event

