package de.nowchess.chess.command

import de.nowchess.api.board.{Square, Board, Color, Piece}
import de.nowchess.chess.logic.GameHistory

/** Marker trait for all commands that can be executed and undone.
 *  Commands encapsulate user actions and game state transitions.
 */
trait Command:
  /** Execute the command and return true if successful, false otherwise. */
  def execute(): Boolean

  /** Undo the command and return true if successful, false otherwise. */
  def undo(): Boolean

  /** A human-readable description of this command. */
  def description: String

/** Command to move a piece from one square to another.
 *  Stores the move result so undo can restore previous state.
 */
case class MoveCommand(
  from: Square,
  to: Square,
  moveResult: Option[MoveResult] = None,
  previousBoard: Option[Board] = None,
  previousHistory: Option[GameHistory] = None,
  previousTurn: Option[Color] = None
) extends Command:

  override def execute(): Boolean =
    moveResult.isDefined

  override def undo(): Boolean =
    previousBoard.isDefined && previousHistory.isDefined && previousTurn.isDefined

  override def description: String = s"Move from $from to $to"

// Sealed hierarchy of move outcomes (for tracking state changes)
sealed trait MoveResult
object MoveResult:
  case class Successful(newBoard: Board, newHistory: GameHistory, newTurn: Color, captured: Option[Piece]) extends MoveResult
  case object InvalidFormat extends MoveResult
  case object InvalidMove extends MoveResult

/** Command to quit the game. */
case class QuitCommand() extends Command:
  override def execute(): Boolean = true
  override def undo(): Boolean = false
  override def description: String = "Quit game"

/** Command to reset the board to initial position. */
case class ResetCommand(
  previousBoard: Option[Board] = None,
  previousHistory: Option[GameHistory] = None,
  previousTurn: Option[Color] = None
) extends Command:

  override def execute(): Boolean = true

  override def undo(): Boolean =
    previousBoard.isDefined && previousHistory.isDefined && previousTurn.isDefined

  override def description: String = "Reset board"
