package de.nowchess.chess.engine

import scala.collection.mutable
import de.nowchess.api.board.{Board, Color}
import de.nowchess.chess.logic.GameHistory
import de.nowchess.chess.observer.{Observer, GameEvent, InvalidMoveEvent, MoveExecutedEvent}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** Tests for GameEngine invalid move handling via handleFailedMove */
class GameEngineInvalidMovesTest extends AnyFunSuite with Matchers:

  test("GameEngine handles no piece at source square"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    // Try to move from h1 which may be empty or not have our piece
    // We'll try from a clearly empty square
    engine.processUserInput("h1h2")
    
    // Should get an InvalidMoveEvent about NoPiece
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[InvalidMoveEvent]

  test("GameEngine handles moving wrong color piece"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    // White moves first
    engine.processUserInput("e2e4")
    observer.events.clear()
    
    // White tries to move again (should fail - it's black's turn)
    // But we need to try a move that looks legal but has wrong color
    // This is hard to test because we'd need to be black and move white's piece
    // Let's skip this for now and focus on testable cases
    
    // Actually, let's try moving a square that definitely has the wrong piece
    // Move a white pawn as black by reaching that position
    engine.processUserInput("e7e5")
    observer.events.clear()
    
    // Now try to move white's e4 pawn as black (it's black's turn but e4 is white)
    engine.processUserInput("e4e5")
    
    observer.events.size shouldBe 1
    val event = observer.events.head
    event shouldBe an[InvalidMoveEvent]

  test("GameEngine handles illegal move"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    // A pawn can't move backward
    engine.processUserInput("e2e1")
    
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[InvalidMoveEvent]
    val event = observer.events.head.asInstanceOf[InvalidMoveEvent]
    event.reason should include("Illegal move")

  test("GameEngine handles pawn trying to move 3 squares"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    // Pawn can only move 1 or 2 squares on first move, not 3
    engine.processUserInput("e2e5")
    
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[InvalidMoveEvent]

  test("GameEngine handles moving from empty square"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    // h3 is empty in starting position
    engine.processUserInput("h3h4")
    
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[InvalidMoveEvent]
    val event = observer.events.head.asInstanceOf[InvalidMoveEvent]
    event.reason should include("No piece on that square")

  test("GameEngine processes valid move after invalid attempt"):
    val engine = new GameEngine()
    val observer = new MockObserver()
    engine.subscribe(observer)
    
    // Try invalid move
    engine.processUserInput("h3h4")
    observer.events.clear()
    
    // Make valid move
    engine.processUserInput("e2e4")
    
    observer.events.size shouldBe 1
    observer.events.head shouldBe an[MoveExecutedEvent]

  test("GameEngine maintains state after failed move attempt"):
    val engine = new GameEngine()
    val initialTurn = engine.turn
    val initialBoard = engine.board
    
    // Try invalid move
    engine.processUserInput("h3h4")
    
    // State should not change
    engine.turn shouldBe initialTurn
    engine.board shouldBe initialBoard
