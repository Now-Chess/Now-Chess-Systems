package de.nowchess.chess.observer

import de.nowchess.api.board.{Board, Color, Square}
import de.nowchess.chess.logic.GameHistory

/** Base trait for all game state events.
 *  Events are immutable snapshots of game state changes.
 */
sealed trait GameEvent:
  def board: Board
  def history: GameHistory
  def turn: Color

/** Fired when a move is successfully executed. */
case class MoveExecutedEvent(
  board: Board,
  history: GameHistory,
  turn: Color,
  fromSquare: String,
  toSquare: String,
  capturedPiece: Option[String]
) extends GameEvent

/** Fired when the current player is in check. */
case class CheckDetectedEvent(
  board: Board,
  history: GameHistory,
  turn: Color
) extends GameEvent

/** Fired when the game reaches checkmate. */
case class CheckmateEvent(
  board: Board,
  history: GameHistory,
  turn: Color,
  winner: Color
) extends GameEvent

/** Fired when the game reaches stalemate. */
case class StalemateEvent(
  board: Board,
  history: GameHistory,
  turn: Color
) extends GameEvent

/** Fired when a move is invalid. */
case class InvalidMoveEvent(
  board: Board,
  history: GameHistory,
  turn: Color,
  reason: String
) extends GameEvent

/** Fired when a pawn reaches the back rank and the player must choose a promotion piece. */
case class PromotionRequiredEvent(
  board: Board,
  history: GameHistory,
  turn: Color,
  from: Square,
  to: Square
) extends GameEvent

/** Fired when the board is reset. */
case class BoardResetEvent(
  board: Board,
  history: GameHistory,
  turn: Color
) extends GameEvent

/** Observer trait: implement to receive game state updates. */
trait Observer:
  def onGameEvent(event: GameEvent): Unit

/** Observable trait: manages observers and notifies them of events. */
trait Observable:
  private val observers = scala.collection.mutable.Set[Observer]()

  /** Register an observer to receive game events. */
  def subscribe(observer: Observer): Unit = synchronized {
    observers += observer
  }

  /** Unregister an observer. */
  def unsubscribe(observer: Observer): Unit = synchronized {
    observers -= observer
  }

  /** Notify all observers of a game event. */
  protected def notifyObservers(event: GameEvent): Unit = synchronized {
    observers.foreach(_.onGameEvent(event))
  }

  /** Return current list of observers (for testing). */
  def observerCount: Int = synchronized {
    observers.size
  }
