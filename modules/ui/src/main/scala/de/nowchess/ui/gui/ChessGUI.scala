package de.nowchess.ui.gui

import javafx.application.{Application => JFXApplication, Platform => JFXPlatform}
import javafx.stage.Stage as JFXStage
import scalafx.application.Platform
import scalafx.scene.Scene
import scalafx.stage.Stage
import de.nowchess.chess.engine.GameEngine

/** ScalaFX GUI Application for Chess.
 *  This is launched from Main alongside the TUI.
 *  Both subscribe to the same GameEngine via Observer pattern.
 */
class ChessGUIApp extends JFXApplication:
  
  override def start(primaryStage: JFXStage): Unit =
    val engine = ChessGUILauncher.getEngine
    val stage = new Stage(primaryStage)
    
    stage.title = "Chess"
    stage.width = 700
    stage.height = 1000
    stage.resizable = false
    
    val boardView = new ChessBoardView(stage, engine)
    val guiObserver = new GUIObserver(boardView)
    
    // Subscribe GUI observer to engine
    engine.subscribe(guiObserver)
    
    stage.scene = new Scene {
      root = boardView
      // Load CSS if available
      try {
        val cssUrl = getClass.getResource("/styles.css")
        if cssUrl != null then
          stylesheets.add(cssUrl.toExternalForm)
      } catch {
        case _: Exception => // CSS is optional
      }
    }
    
    stage.onCloseRequest = _ => {
      // Unsubscribe when window closes
      engine.unsubscribe(guiObserver)
    }
    
    stage.show()

/** Launcher object that holds the engine reference and launches GUI in separate thread. */
object ChessGUILauncher:
  @volatile private var engine: GameEngine = scala.compiletime.uninitialized
  
  def getEngine: GameEngine = engine
  
  def launch(eng: GameEngine): Unit =
    engine = eng
    val guiThread = new Thread(() => {
      JFXApplication.launch(classOf[ChessGUIApp])
    })
    guiThread.setDaemon(false)
    guiThread.setName("ScalaFX-GUI-Thread")
    guiThread.start()
