package de.nowchess.chess.engine

import de.nowchess.chess.observer.{GameEvent, Observer}
import de.nowchess.io.fen.FenParser
import de.nowchess.io.pgn.{PgnExporter, PgnParser}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

class GameEngineLoadGameTest extends AnyFunSuite with Matchers:

  test("loadGame with PgnParser: loads valid PGN and enables undo/redo"):
    val engine = new GameEngine()
    val pgn    = "[Event \"Test\"]\n\n1. e4 e5\n"
    val result = engine.loadGame(PgnParser, pgn)
    result shouldBe Right(())
    engine.context.moves.size shouldBe 2
    engine.canUndo shouldBe true

  test("loadGame with FenParser: loads position without replaying moves"):
    val engine = new GameEngine()
    val fen    = "8/4P3/4k3/8/8/8/8/8 w - - 0 1"
    val result = engine.loadGame(FenParser, fen)
    result shouldBe Right(())
    engine.context.moves.isEmpty shouldBe true
    engine.canUndo shouldBe false

  test("exportGame with PgnExporter: exports current game as PGN"):
    val engine = new GameEngine()
    engine.processUserInput("e2e4")
    engine.processUserInput("e7e5")
    val pgn = engine.exportGame(PgnExporter)
    pgn.contains("e4") shouldBe true
    pgn.contains("e5") shouldBe true

  private class MockObserver extends Observer:
    val events = mutable.ListBuffer[GameEvent]()
    override def onGameEvent(event: GameEvent): Unit =
      events += event
