package de.nowchess.chess.engine

import de.nowchess.api.board.{Board, Color, Piece, Square}
import de.nowchess.api.move.PromotionPiece
import de.nowchess.chess.logic.{GameHistory, GameRules, PositionStatus}
import de.nowchess.chess.controller.{GameController, Parser, MoveResult}
import de.nowchess.chess.observer.*
import de.nowchess.chess.command.{CommandInvoker, MoveCommand}

/** Pure game engine that manages game state and notifies observers of state changes.
 *  This class is the single source of truth for the game state.
 *  All user interactions must go through this engine via Commands, and all state changes
 *  are communicated to observers via GameEvent notifications.
 */
class GameEngine(
  initialBoard: Board = Board.initial,
  initialHistory: GameHistory = GameHistory.empty,
  initialTurn: Color = Color.White,
  completePromotionFn: (Board, GameHistory, Square, Square, PromotionPiece, Color) => MoveResult =
    GameController.completePromotion
) extends Observable:
  private var currentBoard: Board = initialBoard
  private var currentHistory: GameHistory = initialHistory
  private var currentTurn: Color = initialTurn
  private val invoker = new CommandInvoker()

  /** Inner class for tracking pending promotion state */
  private case class PendingPromotion(
    from: Square, to: Square,
    boardBefore: Board, historyBefore: GameHistory,
    turn: Color
  )

  /** Current pending promotion, if any */
  private var pendingPromotion: Option[PendingPromotion] = None

  /** True if a pawn promotion move is pending and needs a piece choice. */
  def isPendingPromotion: Boolean = synchronized { pendingPromotion.isDefined }

  // Synchronized accessors for current state
  def board: Board = synchronized { currentBoard }
  def history: GameHistory = synchronized { currentHistory }
  def turn: Color = synchronized { currentTurn }

  /** Check if undo is available. */
  def canUndo: Boolean = synchronized { invoker.canUndo }

  /** Check if redo is available. */
  def canRedo: Boolean = synchronized { invoker.canRedo }

  /** Get the command history for inspection (testing/debugging). */
  def commandHistory: List[de.nowchess.chess.command.Command] = synchronized { invoker.history }

  /** Process a raw move input string and update game state if valid.
   *  Notifies all observers of the outcome via GameEvent.
   */
  def processUserInput(rawInput: String): Unit = synchronized {
    val trimmed = rawInput.trim.toLowerCase
    trimmed match
      case "quit" | "q" =>
        // Client should handle quit logic; we just return
        ()

      case "undo" =>
        performUndo()

      case "redo" =>
        performRedo()

      case "" =>
        val event = InvalidMoveEvent(
          currentBoard,
          currentHistory,
          currentTurn,
          "Please enter a valid move or command."
        )
        notifyObservers(event)

      case moveInput =>
        // Try to parse as a move
        Parser.parseMove(moveInput) match
          case None =>
            val event = InvalidMoveEvent(
              currentBoard,
              currentHistory,
              currentTurn,
              s"Invalid move format '$moveInput'. Use coordinate notation, e.g. e2e4."
            )
            notifyObservers(event)

          case Some((from, to)) =>
            // Create a move command with current state snapshot
            val cmd = MoveCommand(
              from = from,
              to = to,
              previousBoard = Some(currentBoard),
              previousHistory = Some(currentHistory),
              previousTurn = Some(currentTurn)
            )

            // Execute the move through GameController
            GameController.processMove(currentBoard, currentHistory, currentTurn, moveInput) match
              case MoveResult.InvalidFormat(_) | MoveResult.NoPiece | MoveResult.WrongColor | MoveResult.IllegalMove | MoveResult.Quit =>
                handleFailedMove(moveInput)

              case MoveResult.Moved(newBoard, newHistory, captured, newTurn) =>
                // Move succeeded - store result and execute through invoker
                val updatedCmd = cmd.copy(moveResult = Some(de.nowchess.chess.command.MoveResult.Successful(newBoard, newHistory, newTurn, captured)))
                invoker.execute(updatedCmd)
                updateGameState(newBoard, newHistory, newTurn)
                emitMoveEvent(from.toString, to.toString, captured, newTurn)

              case MoveResult.MovedInCheck(newBoard, newHistory, captured, newTurn) =>
                // Move succeeded with check
                val updatedCmd = cmd.copy(moveResult = Some(de.nowchess.chess.command.MoveResult.Successful(newBoard, newHistory, newTurn, captured)))
                invoker.execute(updatedCmd)
                updateGameState(newBoard, newHistory, newTurn)
                emitMoveEvent(from.toString, to.toString, captured, newTurn)
                notifyObservers(CheckDetectedEvent(currentBoard, currentHistory, currentTurn))

              case MoveResult.Checkmate(winner) =>
                // Move resulted in checkmate
                val updatedCmd = cmd.copy(moveResult = Some(de.nowchess.chess.command.MoveResult.Successful(Board.initial, GameHistory.empty, Color.White, None)))
                invoker.execute(updatedCmd)
                currentBoard = Board.initial
                currentHistory = GameHistory.empty
                currentTurn = Color.White
                notifyObservers(CheckmateEvent(currentBoard, currentHistory, currentTurn, winner))

              case MoveResult.Stalemate =>
                // Move resulted in stalemate
                val updatedCmd = cmd.copy(moveResult = Some(de.nowchess.chess.command.MoveResult.Successful(Board.initial, GameHistory.empty, Color.White, None)))
                invoker.execute(updatedCmd)
                currentBoard = Board.initial
                currentHistory = GameHistory.empty
                currentTurn = Color.White
                notifyObservers(StalemateEvent(currentBoard, currentHistory, currentTurn))

              case MoveResult.PromotionRequired(promFrom, promTo, boardBefore, histBefore, _, promotingTurn) =>
                pendingPromotion = Some(PendingPromotion(promFrom, promTo, boardBefore, histBefore, promotingTurn))
                notifyObservers(PromotionRequiredEvent(currentBoard, currentHistory, currentTurn, promFrom, promTo))
  }

  /** Undo the last move. */
  def undo(): Unit = synchronized {
    performUndo()
  }

  /** Redo the last undone move. */
  def redo(): Unit = synchronized {
    performRedo()
  }

  /** Apply a player's promotion piece choice.
   *  Must only be called when isPendingPromotion is true.
   */
  def completePromotion(piece: PromotionPiece): Unit = synchronized {
    pendingPromotion match
      case None =>
        notifyObservers(InvalidMoveEvent(currentBoard, currentHistory, currentTurn, "No promotion pending."))
      case Some(pending) =>
        pendingPromotion = None
        val cmd = MoveCommand(
          from = pending.from,
          to = pending.to,
          previousBoard = Some(pending.boardBefore),
          previousHistory = Some(pending.historyBefore),
          previousTurn = Some(pending.turn)
        )
        completePromotionFn(
          pending.boardBefore, pending.historyBefore,
          pending.from, pending.to, piece, pending.turn
        ) match
          case MoveResult.Moved(newBoard, newHistory, captured, newTurn) =>
            val updatedCmd = cmd.copy(moveResult = Some(de.nowchess.chess.command.MoveResult.Successful(newBoard, newHistory, newTurn, captured)))
            invoker.execute(updatedCmd)
            updateGameState(newBoard, newHistory, newTurn)
            emitMoveEvent(pending.from.toString, pending.to.toString, captured, newTurn)

          case MoveResult.MovedInCheck(newBoard, newHistory, captured, newTurn) =>
            val updatedCmd = cmd.copy(moveResult = Some(de.nowchess.chess.command.MoveResult.Successful(newBoard, newHistory, newTurn, captured)))
            invoker.execute(updatedCmd)
            updateGameState(newBoard, newHistory, newTurn)
            emitMoveEvent(pending.from.toString, pending.to.toString, captured, newTurn)
            notifyObservers(CheckDetectedEvent(currentBoard, currentHistory, currentTurn))

          case MoveResult.Checkmate(winner) =>
            val updatedCmd = cmd.copy(moveResult = Some(de.nowchess.chess.command.MoveResult.Successful(Board.initial, GameHistory.empty, Color.White, None)))
            invoker.execute(updatedCmd)
            currentBoard = Board.initial
            currentHistory = GameHistory.empty
            currentTurn = Color.White
            notifyObservers(CheckmateEvent(currentBoard, currentHistory, currentTurn, winner))

          case MoveResult.Stalemate =>
            val updatedCmd = cmd.copy(moveResult = Some(de.nowchess.chess.command.MoveResult.Successful(Board.initial, GameHistory.empty, Color.White, None)))
            invoker.execute(updatedCmd)
            currentBoard = Board.initial
            currentHistory = GameHistory.empty
            currentTurn = Color.White
            notifyObservers(StalemateEvent(currentBoard, currentHistory, currentTurn))

          case _ =>
            notifyObservers(InvalidMoveEvent(currentBoard, currentHistory, currentTurn, "Error completing promotion."))
  }

  /** Reset the board to initial position. */
  def reset(): Unit = synchronized {
    currentBoard = Board.initial
    currentHistory = GameHistory.empty
    currentTurn = Color.White
    invoker.clear()
    notifyObservers(BoardResetEvent(
      currentBoard,
      currentHistory,
      currentTurn
    ))
  }

  // ──── Private Helpers ────

  private def performUndo(): Unit =
    if invoker.canUndo then
      val cmd = invoker.history(invoker.getCurrentIndex)
      (cmd: @unchecked) match
        case moveCmd: MoveCommand =>
          moveCmd.previousBoard.foreach(currentBoard = _)
          moveCmd.previousHistory.foreach(currentHistory = _)
          moveCmd.previousTurn.foreach(currentTurn = _)
          invoker.undo()
          notifyObservers(BoardResetEvent(currentBoard, currentHistory, currentTurn))
    else
      notifyObservers(InvalidMoveEvent(currentBoard, currentHistory, currentTurn, "Nothing to undo."))

  private def performRedo(): Unit =
    if invoker.canRedo then
      val cmd = invoker.history(invoker.getCurrentIndex + 1)
      (cmd: @unchecked) match
        case moveCmd: MoveCommand =>
          for case de.nowchess.chess.command.MoveResult.Successful(nb, nh, nt, cap) <- moveCmd.moveResult do
            updateGameState(nb, nh, nt)
            invoker.redo()
            emitMoveEvent(moveCmd.from.toString, moveCmd.to.toString, cap, nt)
    else
      notifyObservers(InvalidMoveEvent(currentBoard, currentHistory, currentTurn, "Nothing to redo."))

  private def updateGameState(newBoard: Board, newHistory: GameHistory, newTurn: Color): Unit =
    currentBoard = newBoard
    currentHistory = newHistory
    currentTurn = newTurn

  private def emitMoveEvent(fromSq: String, toSq: String, captured: Option[Piece], newTurn: Color): Unit =
    val capturedDesc = captured.map(c => s"${c.color.label} ${c.pieceType.label}")
    notifyObservers(MoveExecutedEvent(
      currentBoard,
      currentHistory,
      newTurn,
      fromSq,
      toSq,
      capturedDesc
    ))

  private def handleFailedMove(moveInput: String): Unit =
    (GameController.processMove(currentBoard, currentHistory, currentTurn, moveInput): @unchecked) match
      case MoveResult.NoPiece =>
        notifyObservers(InvalidMoveEvent(
          currentBoard,
          currentHistory,
          currentTurn,
          "No piece on that square."
        ))
      case MoveResult.WrongColor =>
        notifyObservers(InvalidMoveEvent(
          currentBoard,
          currentHistory,
          currentTurn,
          "That is not your piece."
        ))
      case MoveResult.IllegalMove =>
        notifyObservers(InvalidMoveEvent(
          currentBoard,
          currentHistory,
          currentTurn,
          "Illegal move."
        ))

