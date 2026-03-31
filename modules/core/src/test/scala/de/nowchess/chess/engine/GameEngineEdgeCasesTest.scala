package de.nowchess.chess.engine

import scala.collection.mutable
import de.nowchess.api.board.{Board, Color}
import de.nowchess.chess.logic.GameHistory
import de.nowchess.chess.observer.{Observer, GameEvent, MoveExecutedEvent, CheckDetectedEvent, BoardResetEvent, InvalidMoveEvent}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** Tests for GameEngine edge cases and uncovered paths */
class GameEngineEdgeCasesTest extends AnyFunSuite with Matchers:

  test("GameEngine handles empty input"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("")
    
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[InvalidMoveEvent]
    val event = observer.events.head.asInstanceOf[InvalidMoveEvent]
    event.reason should include("Please enter a valid move or command")

  test("GameEngine processes quit command"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("quit")
    // Quit just returns, no events
    observer.events.isEmpty shouldBe true

  test("GameEngine processes q command (short form)"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("q")
    observer.events.isEmpty shouldBe true

  test("GameEngine handles uppercase quit"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("QUIT")
    observer.events.isEmpty shouldBe true

  test("GameEngine handles undo on empty history"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.canUndo shouldBe false
    engine.processUserInput("undo")
    
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[InvalidMoveEvent]
    val event = observer.events.head.asInstanceOf[InvalidMoveEvent]
    event.reason should include("Nothing to undo")

  test("GameEngine handles redo on empty redo history"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.canRedo shouldBe false
    engine.processUserInput("redo")
    
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[InvalidMoveEvent]
    val event = observer.events.head.asInstanceOf[InvalidMoveEvent]
    event.reason should include("Nothing to redo")

  test("GameEngine parses invalid move format"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("invalid_move_format")
    
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[InvalidMoveEvent]
    val event = observer.events.head.asInstanceOf[InvalidMoveEvent]
    event.reason should include("Invalid move format")

  test("GameEngine handles lowercase input normalization"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("  UNDO  ")  // With spaces and uppercase
    
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[InvalidMoveEvent]  // No moves to undo yet

  test("GameEngine preserves board state on invalid move"):
    val engine = new GameEngine()
    val initialBoard = engine.board
    
    engine.processUserInput("invalid")
    
    engine.board shouldBe initialBoard

  test("GameEngine preserves turn on invalid move"):
    val engine = new GameEngine()
    val initialTurn = engine.turn
    
    engine.processUserInput("invalid")
    
    engine.turn shouldBe initialTurn

  test("GameEngine undo with no commands available"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    // Make a valid move
    engine.processUserInput("e2e4")
    observer.events.clear()
    
    // Undo it
    engine.processUserInput("undo")
    
    // Board should be reset
    engine.board shouldBe Board.initial
    engine.turn shouldBe Color.White

  test("GameEngine redo after undo"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("e2e4")
    val boardAfterMove = engine.board
    val turnAfterMove = engine.turn
    observer.events.clear()
    
    engine.processUserInput("undo")
    engine.processUserInput("redo")
    
    engine.board shouldBe boardAfterMove
    engine.turn shouldBe turnAfterMove

  test("GameEngine canUndo flag tracks state correctly"):
    val engine = new GameEngine()
    
    engine.canUndo shouldBe false
    engine.processUserInput("e2e4")
    engine.canUndo shouldBe true
    engine.processUserInput("undo")
    engine.canUndo shouldBe false

  test("GameEngine canRedo flag tracks state correctly"):
    val engine = new GameEngine()
    
    engine.canRedo shouldBe false
    engine.processUserInput("e2e4")
    engine.canRedo shouldBe false
    engine.processUserInput("undo")
    engine.canRedo shouldBe true

  test("GameEngine command history is accessible"):
    val engine = new GameEngine()
    
    engine.commandHistory.isEmpty shouldBe true
    engine.processUserInput("e2e4")
    engine.commandHistory.size shouldBe 1

  test("GameEngine processes multiple moves in sequence"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    observer.events.clear()
    
    engine.processUserInput("e2e4")
    engine.processUserInput("e7e5")
    
    observer.events.size shouldBe 2
    engine.commandHistory.size shouldBe 2

  test("GameEngine can undo multiple moves"):
    val engine = new GameEngine()
    
    engine.processUserInput("e2e4")
    engine.processUserInput("e7e5")
    
    engine.processUserInput("undo")
    engine.turn shouldBe Color.Black
    
    engine.processUserInput("undo")
    engine.turn shouldBe Color.White

  test("GameEngine thread-safe operations"):
    val engine = new GameEngine()
    
    // Access from synchronized methods
    val board = engine.board
    val history = engine.history
    val turn = engine.turn
    val canUndo = engine.canUndo
    val canRedo = engine.canRedo
    
    board shouldBe Board.initial
    canUndo shouldBe false
    canRedo shouldBe false


private class MockObserver extends Observer:
  val events = mutable.ListBuffer[GameEvent]()
  
  override def onGameEvent(event: GameEvent): Unit =
    events += event
