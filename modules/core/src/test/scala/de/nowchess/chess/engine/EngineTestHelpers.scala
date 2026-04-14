package de.nowchess.chess.engine

import de.nowchess.api.board.{Board, Color}
import de.nowchess.api.game.GameContext
import de.nowchess.chess.observer.*
import de.nowchess.io.fen.FenParser
import de.nowchess.rules.sets.DefaultRules
import scala.collection.mutable

object EngineTestHelpers:

  def makeEngine(): GameEngine =
    new GameEngine(ruleSet = DefaultRules)

  def makeEngineWithBoard(board: Board, turn: Color = Color.White): GameEngine =
    GameEngine(initialContext = GameContext.initial.withBoard(board).withTurn(turn))

  def loadFen(engine: GameEngine, fen: String): Unit =
    engine.loadGame(FenParser, fen)

  def captureEvents(engine: GameEngine): mutable.ListBuffer[GameEvent] =
    val events = mutable.ListBuffer[GameEvent]()
    engine.subscribe(new Observer { def onGameEvent(e: GameEvent): Unit = events += e })
    events

  class MockObserver extends Observer:
    private val _events = mutable.ListBuffer[GameEvent]()

    def events: mutable.ListBuffer[GameEvent] = _events
    def eventCount: Int                       = _events.length
    def hasEvent[T <: GameEvent](implicit ct: scala.reflect.ClassTag[T]): Boolean =
      _events.exists(ct.runtimeClass.isInstance(_))
    def getEvent[T <: GameEvent](implicit ct: scala.reflect.ClassTag[T]): Option[T] =
      _events.collectFirst { case e: T => e }

    override def onGameEvent(event: GameEvent): Unit =
      _events += event

    def clear(): Unit =
      _events.clear()
