package de.nowchess.ui.gui

import scalafx.application.Platform
import scalafx.scene.control.Alert
import scalafx.scene.control.Alert.AlertType
import de.nowchess.chess.observer.{Observer, GameEvent, *}
import de.nowchess.api.board.Board

/** GUI Observer that implements the Observer pattern.
 *  Receives game events from GameEngine and updates the ScalaFX UI.
 *  All UI updates must be done on the JavaFX Application Thread.
 */
class GUIObserver(private val boardView: ChessBoardView) extends Observer:

  override def onGameEvent(event: GameEvent): Unit =
    // Ensure UI updates happen on JavaFX thread
    Platform.runLater {
      event match
        case e: MoveExecutedEvent =>
          boardView.updateBoard(e.board, e.turn)
          e.capturedPiece.foreach { piece =>
            boardView.showMessage(s"Captured: $piece on ${e.toSquare}")
          }

        case e: CheckDetectedEvent =>
          boardView.updateBoard(e.board, e.turn)
          boardView.showMessage(s"${e.turn.label} is in check!")

        case e: CheckmateEvent =>
          boardView.updateBoard(e.board, e.turn)
          showAlert(AlertType.Information, "Game Over", s"Checkmate! ${e.winner.label} wins.")

        case e: StalemateEvent =>
          boardView.updateBoard(e.board, e.turn)
          showAlert(AlertType.Information, "Game Over", "Stalemate! The game is a draw.")

        case e: InvalidMoveEvent =>
          boardView.showMessage(s"⚠️  ${e.reason}")

        case e: BoardResetEvent =>
          boardView.updateBoard(e.board, e.turn)
          boardView.showMessage("Board has been reset to initial position.")

        case e: PromotionRequiredEvent =>
          boardView.showPromotionDialog(e.from, e.to)

        case e: DrawClaimedEvent =>
          boardView.updateBoard(e.board, e.turn)
          showAlert(AlertType.Information, "Draw Claimed", "Draw claimed! The game is a draw.")
        case e: FiftyMoveRuleAvailableEvent => 
          boardView.showMessage("50-move rule available! The game is a draw.")
    }

  private def showAlert(alertType: AlertType, titleText: String, content: String): Unit =
    new Alert(alertType) {
      initOwner(boardView.stage)
      title = titleText
      headerText = None
      contentText = content
    }.showAndWait()
