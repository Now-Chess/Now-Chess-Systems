package de.nowchess.chess.command

import de.nowchess.api.board.{Piece, Square}
import de.nowchess.api.game.GameContext

/** Marker trait for all commands that can be executed and undone. Commands encapsulate user actions and game state
  * transitions.
  */
trait Command:
  /** Execute the command and return true if successful, false otherwise. */
  def execute(): Boolean

  /** Undo the command and return true if successful, false otherwise. */
  def undo(): Boolean

  /** A human-readable description of this command. */
  def description: String

/** Command to move a piece from one square to another. Stores the move result so undo can restore previous state.
  */
case class MoveCommand(
    from: Square,
    to: Square,
    moveResult: Option[MoveResult] = None,
    previousContext: Option[GameContext] = None,
    notation: String = "",
) extends Command:

  override def execute(): Boolean =
    moveResult.isDefined

  override def undo(): Boolean =
    previousContext.isDefined

  override def description: String = s"Move from $from to $to"

// Sealed hierarchy of move outcomes (for tracking state changes)
sealed trait MoveResult
object MoveResult:
  case class Successful(newContext: GameContext, captured: Option[Piece]) extends MoveResult
  case object InvalidFormat                                               extends MoveResult
  case object InvalidMove                                                 extends MoveResult

/** Command to quit the game. */
case class QuitCommand() extends Command:
  override def execute(): Boolean  = true
  override def undo(): Boolean     = false
  override def description: String = "Quit game"

/** Command to reset the board to initial position. */
case class ResetCommand(
    previousContext: Option[GameContext] = None,
) extends Command:

  override def execute(): Boolean = true

  override def undo(): Boolean =
    previousContext.isDefined

  override def description: String = "Reset board"
