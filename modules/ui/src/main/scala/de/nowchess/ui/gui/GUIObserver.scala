package de.nowchess.ui.gui

import scalafx.application.Platform
import scalafx.scene.control.Alert
import scalafx.scene.control.Alert.AlertType
import de.nowchess.chess.observer.{GameEvent, Observer, *}
import de.nowchess.api.board.Board
import de.nowchess.api.game.DrawReason

/** GUI Observer that implements the Observer pattern. Receives game events from GameEngine and updates the ScalaFX UI.
  * All UI updates must be done on the JavaFX Application Thread.
  */
class GUIObserver(private val boardView: ChessBoardView) extends Observer:

  override def onGameEvent(event: GameEvent): Unit =
    // Ensure UI updates happen on JavaFX thread
    Platform.runLater {
      event match
        case e: MoveExecutedEvent =>
          boardView.updateBoard(e.context.board, e.context.turn)
          e.capturedPiece.foreach { piece =>
            boardView.showMessage(s"Captured: $piece on ${e.toSquare}")
          }

        case e: CheckDetectedEvent =>
          boardView.updateBoard(e.context.board, e.context.turn)
          boardView.showMessage(s"${e.context.turn.label} is in check!")

        case e: CheckmateEvent =>
          boardView.updateBoard(e.context.board, e.context.turn)
          showAlert(AlertType.Information, "Game Over", s"Checkmate! ${e.winner.label} wins.")

        case e: DrawEvent =>
          boardView.updateBoard(e.context.board, e.context.turn)
          val msg = e.reason match
            case DrawReason.Stalemate            => "Stalemate! The game is a draw."
            case DrawReason.InsufficientMaterial => "Draw by insufficient material."
            case DrawReason.FiftyMoveRule        => "Draw claimed under the 50-move rule."
            case DrawReason.ThreefoldRepetition  => "Draw by threefold repetition."
            case DrawReason.Agreement            => "Draw by agreement."
          showAlert(AlertType.Information, "Game Over", msg)

        case e: InvalidMoveEvent =>
          boardView.showMessage(s"⚠️  ${e.reason}")

        case e: BoardResetEvent =>
          boardView.updateBoard(e.context.board, e.context.turn)
          boardView.showMessage("Board has been reset to initial position.")

        case e: FiftyMoveRuleAvailableEvent =>
          boardView.showMessage("50-move rule is now available — type 'draw' to claim.")

        case e: ThreefoldRepetitionAvailableEvent =>
          boardView.showMessage("Threefold repetition is now available — type 'draw' to claim.")

        case e: MoveUndoneEvent =>
          boardView.updateBoard(e.context.board, e.context.turn)
          boardView.showMessage(s"↶ Undo: ${e.pgnNotation}")
          boardView.updateUndoRedoButtons()

        case e: MoveRedoneEvent =>
          boardView.updateBoard(e.context.board, e.context.turn)
          if e.capturedPiece.isDefined then
            boardView.showMessage(s"↷ Redo: ${e.pgnNotation} — Captured: ${e.capturedPiece.get}")
          else boardView.showMessage(s"↷ Redo: ${e.pgnNotation}")
          boardView.updateUndoRedoButtons()

        case e: PgnLoadedEvent =>
          boardView.updateBoard(e.context.board, e.context.turn)
          boardView.showMessage("✓ PGN loaded successfully!")
          boardView.updateUndoRedoButtons()
    }

  private def showAlert(alertType: AlertType, titleText: String, content: String): Unit =
    new Alert(alertType) {
      initOwner(boardView.stage)
      title = titleText
      headerText = None
      contentText = content
    }.showAndWait()
