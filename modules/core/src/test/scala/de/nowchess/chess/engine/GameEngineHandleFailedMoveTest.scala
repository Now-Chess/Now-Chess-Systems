package de.nowchess.chess.engine

import scala.collection.mutable
import de.nowchess.api.board.{Board, Color}
import de.nowchess.chess.logic.GameHistory
import de.nowchess.chess.observer.{Observer, GameEvent, InvalidMoveEvent}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** Tests to maximize handleFailedMove coverage */
class GameEngineHandleFailedMoveTest extends AnyFunSuite with Matchers:

  test("GameEngine handles InvalidFormat error type"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("not_a_valid_move_format")
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[InvalidMoveEvent]
    val msg1 = observer.events.head.asInstanceOf[InvalidMoveEvent].reason
    msg1 should include("Invalid move format")

  test("GameEngine handles NoPiece error type"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("h3h4")
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[InvalidMoveEvent]
    val msg2 = observer.events.head.asInstanceOf[InvalidMoveEvent].reason
    msg2 should include("No piece on that square")

  test("GameEngine handles WrongColor error type"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("e2e4")  // White move
    observer.events.clear()
    
    engine.processUserInput("a1b2")  // Try to move black's rook position with white's move (wrong color)
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[InvalidMoveEvent]
    val msg3 = observer.events.head.asInstanceOf[InvalidMoveEvent].reason
    msg3 should include("That is not your piece")

  test("GameEngine handles IllegalMove error type"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("e2e1")  // Try pawn backward
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[InvalidMoveEvent]
    val msg4 = observer.events.head.asInstanceOf[InvalidMoveEvent].reason
    msg4 should include("Illegal move")

  test("GameEngine invalid move message for InvalidFormat"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("xyz123")
    val event = observer.events.head.asInstanceOf[InvalidMoveEvent]
    event.reason should include("coordinate notation")

  test("GameEngine invalid move message for NoPiece"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("a3a4")  // a3 is empty
    val event = observer.events.head.asInstanceOf[InvalidMoveEvent]
    event.reason should include("No piece")

  test("GameEngine invalid move message for WrongColor"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("e2e4")
    observer.events.clear()
    
    engine.processUserInput("e4e5")  // e4 has white pawn, it's black's turn
    val event = observer.events.head.asInstanceOf[InvalidMoveEvent]
    event.reason should include("not your piece")

  test("GameEngine invalid move message for IllegalMove"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    engine.processUserInput("e2e1")  // Pawn can't move backward
    val event = observer.events.head.asInstanceOf[InvalidMoveEvent]
    event.reason should include("Illegal move")

  test("GameEngine board unchanged after each type of invalid move"):
    val engine = new GameEngine()
    val initial = engine.board
    
    engine.processUserInput("invalid")
    engine.board shouldBe initial
    
    engine.processUserInput("h3h4")
    engine.board shouldBe initial
    
    engine.processUserInput("e2e1")
    engine.board shouldBe initial
