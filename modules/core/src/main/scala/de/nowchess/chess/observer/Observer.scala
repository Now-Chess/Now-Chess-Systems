package de.nowchess.chess.observer

import de.nowchess.api.board.{Color, Square}
import de.nowchess.api.game.{DrawReason, GameContext}

/** Base trait for all game state events. Events are immutable snapshots of game state changes.
  */
sealed trait GameEvent:
  def context: GameContext

/** Fired when a move is successfully executed. */
case class MoveExecutedEvent(
    context: GameContext,
    fromSquare: String,
    toSquare: String,
    capturedPiece: Option[String],
) extends GameEvent

/** Fired when the current player is in check. */
case class CheckDetectedEvent(
    context: GameContext,
) extends GameEvent

/** Fired when the game reaches checkmate. */
case class CheckmateEvent(
    context: GameContext,
    winner: Color,
) extends GameEvent

/** Fired when the game ends in a draw. */
case class DrawEvent(
    context: GameContext,
    reason: DrawReason,
) extends GameEvent

/** Fired when a move is invalid. */
case class InvalidMoveEvent(
    context: GameContext,
    reason: String,
) extends GameEvent

/** Fired when a pawn reaches the back rank and the player must choose a promotion piece. */
case class PromotionRequiredEvent(
    context: GameContext,
    from: Square,
    to: Square,
) extends GameEvent

/** Fired when the board is reset. */
case class BoardResetEvent(
    context: GameContext,
) extends GameEvent

/** Fired after any move where the half-move clock reaches 100 — the 50-move rule is now claimable. */
case class FiftyMoveRuleAvailableEvent(
    context: GameContext,
) extends GameEvent

/** Fired when a move is undone, carrying PGN notation of the reversed move. */
case class MoveUndoneEvent(
    context: GameContext,
    pgnNotation: String,
) extends GameEvent

/** Fired when a previously undone move is redone, carrying PGN notation of the replayed move. */
case class MoveRedoneEvent(
    context: GameContext,
    pgnNotation: String,
    fromSquare: String,
    toSquare: String,
    capturedPiece: Option[String],
) extends GameEvent

/** Fired after a PGN string is successfully loaded and all moves are replayed into history. */
case class PgnLoadedEvent(
    context: GameContext,
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
