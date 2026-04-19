package de.nowchess.ui.gui

import java.util.concurrent.atomic.AtomicReference
import scalafx.Includes.*
import scalafx.application.Platform
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.{Button, ButtonType, ChoiceDialog, Label}
import scalafx.scene.layout.{BorderPane, GridPane, HBox, StackPane, VBox}
import scalafx.scene.paint.Color as FXColor
import scalafx.scene.shape.Rectangle
import scalafx.scene.text.{Font, Text}
import scalafx.stage.Stage
import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Rank, Square}
import de.nowchess.api.move.MoveType
import de.nowchess.chess.command.{MoveCommand, MoveResult}
import de.nowchess.chess.engine.GameEngine
import de.nowchess.io.fen.{FenExporter, FenParser}
import de.nowchess.io.pgn.{PgnExporter, PgnParser}
import de.nowchess.io.json.{JsonExporter, JsonParser}
import de.nowchess.io.{FileSystemGameService, GameContextExport, GameContextImport, GameFileService}
import java.nio.file.Paths
import scalafx.stage.FileChooser
import scalafx.stage.FileChooser.ExtensionFilter

/** ScalaFX chess board view that displays the game state. Uses chess sprites and color palette. Handles user
  * interactions (clicks) and sends moves to GameEngine.
  */
class ChessBoardView(val stage: Stage, private val engine: GameEngine) extends BorderPane:

  private val squareSize          = 70.0
  private val comicSansFontFamily = "Comic Sans MS"
  private val boardGrid           = new GridPane()
  private val messageLabel = new Label {
    text = "Welcome!"
    font = Font.font(comicSansFontFamily, 16)
    padding = Insets(10)
  }

  private val currentBoard   = new AtomicReference[Board](engine.board)
  private val currentTurn    = new AtomicReference[Color](engine.turn)
  private val selectedSquare = new AtomicReference[Option[Square]](None)
  private val squareViews    = scala.collection.mutable.Map[(Int, Int), StackPane]()

  private val undoButton: Button = new Button("Undo") {
    font = Font.font(comicSansFontFamily, 12)
    onAction = _ => if engine.canUndo then engine.undo()
    style = "-fx-background-radius: 8; -fx-background-color: #B9DAD1;"
    disable = !engine.canUndo
  }
  private val redoButton: Button = new Button("Redo") {
    font = Font.font(comicSansFontFamily, 12)
    onAction = _ => if engine.canRedo then engine.redo()
    style = "-fx-background-radius: 8; -fx-background-color: #B9C2DA;"
    disable = !engine.canRedo
  }

  // Initialize UI
  initializeBoard()

  top = new VBox {
    padding = Insets(10)
    spacing = 5
    alignment = Pos.Center
    children = Seq(
      new Label {
        text = "Chess"
        font = Font.font(comicSansFontFamily, 24)
        style = "-fx-font-weight: bold;"
      },
      messageLabel,
    )
  }

  center = new VBox {
    padding = Insets(20)
    alignment = Pos.Center
    style = s"-fx-background-color: ${PieceSprites.SquareColors.Border};"
    children = boardGrid
  }

  bottom = new VBox {
    padding = Insets(10)
    spacing = 8
    alignment = Pos.Center
    children = Seq(
      new HBox {
        spacing = 10
        alignment = Pos.Center
        children = Seq(
          undoButton,
          redoButton,
          new Button("Reset") {
            font = Font.font(comicSansFontFamily, 12)
            onAction = _ => engine.reset()
            style = "-fx-background-radius: 8; -fx-background-color: #E1EAA9;"
          },
        )
      },
      new HBox {
        spacing = 10
        alignment = Pos.Center
        children = Seq(
          new Button("FEN Export") {
            font = Font.font(comicSansFontFamily, 12)
            onAction = _ => doFenExport()
            style = "-fx-background-radius: 8; -fx-background-color: #DAC4B9;"
          },
          new Button("FEN Import") {
            font = Font.font(comicSansFontFamily, 12)
            onAction = _ => doFenImport()
            style = "-fx-background-radius: 8; -fx-background-color: #DAD4B9;"
          },
          new Button("PGN Export") {
            font = Font.font(comicSansFontFamily, 12)
            onAction = _ => doPgnExport()
            style = "-fx-background-radius: 8; -fx-background-color: #C4DAB9;"
          },
          new Button("PGN Import") {
            font = Font.font(comicSansFontFamily, 12)
            onAction = _ => doPgnImport()
            style = "-fx-background-radius: 8; -fx-background-color: #B9DAC4;"
          },
        )
      },
      new HBox {
        spacing = 10
        alignment = Pos.Center
        children = Seq(
          new Button("JSON Export") {
            font = Font.font(comicSansFontFamily, 12)
            onAction = _ => doJsonExport()
            style = "-fx-background-radius: 8; -fx-background-color: #B9C4DA;"
          },
          new Button("JSON Import") {
            font = Font.font(comicSansFontFamily, 12)
            onAction = _ => doJsonImport()
            style = "-fx-background-radius: 8; -fx-background-color: #C4B9DA;"
          },
        )
      },
    )
  }

  private def initializeBoard(): Unit =
    boardGrid.padding = Insets(5)
    boardGrid.hgap = 0
    boardGrid.vgap = 0

    // Create 8x8 board with rank/file labels
    for
      rank <- 0 until 8
      file <- 0 until 8
    do
      val square = createSquare(rank, file)
      squareViews((rank, file)) = square
      boardGrid.add(square, file, 7 - rank) // Flip rank for proper display

    updateBoard(currentBoard.get(), currentTurn.get())

  private def createSquare(rank: Int, file: Int): StackPane =
    val isWhite   = (rank + file) % 2 == 0
    val baseColor = if isWhite then PieceSprites.SquareColors.White else PieceSprites.SquareColors.Black

    val bgRect = new Rectangle {
      width = squareSize
      height = squareSize
      fill = FXColor.web(baseColor)
      arcWidth = 8
      arcHeight = 8
    }

    val square = new StackPane {
      children = Seq(bgRect)
      onMouseClicked = _ => handleSquareClick(rank, file)
      style = "-fx-cursor: hand;"
    }

    square

  private def handleSquareClick(rank: Int, file: Int): Unit =
    val clickedSquare = Square(File.values(file), Rank.values(rank))

    selectedSquare.get() match
      case None =>
        // First click - select piece if it belongs to current player
        currentBoard.get().pieceAt(clickedSquare).foreach { piece =>
          if piece.color == currentTurn.get() then
            selectedSquare.set(Some(clickedSquare))
            highlightSquare(rank, file, PieceSprites.SquareColors.Selected)

            val legalDests = engine.ruleSet
              .legalMoves(engine.context)(clickedSquare)
              .collect { case move if move.from == clickedSquare => move.to }
            legalDests.foreach { sq =>
              highlightSquare(sq.rank.ordinal, sq.file.ordinal, PieceSprites.SquareColors.ValidMove)
            }
        }

      case Some(fromSquare) =>
        // Second click - attempt move
        if clickedSquare == fromSquare then
          // Deselect
          selectedSquare.set(None)
          updateBoard(currentBoard.get(), currentTurn.get())
        else
          val isPromo = engine.ruleSet
            .legalMoves(engine.context)(fromSquare)
            .exists(m =>
              m.to == clickedSquare && (m.moveType match
                case MoveType.Promotion(_) => true
                case _                     => false
              ),
            )
          if isPromo then showPromotionDialog(fromSquare, clickedSquare)
          else engine.processUserInput(s"${fromSquare}$clickedSquare")
          selectedSquare.set(None)

  def updateBoard(board: Board, turn: Color): Unit =
    currentBoard.set(board)
    currentTurn.set(turn)
    selectedSquare.set(None)

    // Update all squares
    for
      rank <- 0 until 8
      file <- 0 until 8
    do
      squareViews.get((rank, file)).foreach { stackPane =>
        val isWhite   = (rank + file) % 2 == 0
        val baseColor = if isWhite then PieceSprites.SquareColors.White else PieceSprites.SquareColors.Black

        val bgRect = new Rectangle {
          width = squareSize
          height = squareSize
          fill = FXColor.web(baseColor)
          arcWidth = 8
          arcHeight = 8
        }

        val square      = Square(File.values(file), Rank.values(rank))
        val pieceOption = board.pieceAt(square)

        val children: Seq[scalafx.scene.Node] = pieceOption match
          case Some(piece) =>
            Seq(bgRect) ++ PieceSprites.loadPieceImage(piece, squareSize * 0.8).toSeq
          case None =>
            Seq(bgRect)

        stackPane.children = children
      }

    updateUndoRedoButtons()

  def updateUndoRedoButtons(): Unit =
    undoButton.disable = !engine.canUndo
    redoButton.disable = !engine.canRedo

  private def highlightSquare(rank: Int, file: Int, color: String): Unit =
    squareViews.get((rank, file)).foreach { stackPane =>
      val bgRect = new Rectangle {
        width = squareSize
        height = squareSize
        fill = FXColor.web(color)
        arcWidth = 8
        arcHeight = 8
      }

      val square      = Square(File.values(file), Rank.values(rank))
      val pieceOption = currentBoard.get().pieceAt(square)

      stackPane.children = (pieceOption match
        case Some(piece) =>
          Seq(bgRect) ++ PieceSprites.loadPieceImage(piece, squareSize * 0.8).toSeq
        case None =>
          Seq(bgRect)
      ): Seq[scalafx.scene.Node]
    }

  def showMessage(msg: String): Unit =
    messageLabel.text = msg

  def showPromotionDialog(from: Square, to: Square): Unit =
    val choices = Seq("Queen", "Rook", "Bishop", "Knight")
    val dialog = new ChoiceDialog(defaultChoice = "Queen", choices = choices) {
      initOwner(stage)
      title = "Pawn Promotion"
      headerText = "Choose promotion piece"
      contentText = "Promote to:"
    }
    val uciSuffix = dialog.showAndWait() match
      case Some("Rook")   => "r"
      case Some("Bishop") => "b"
      case Some("Knight") => "n"
      case _              => "q"
    engine.processUserInput(s"${from}${to}$uciSuffix")

  private def doFenExport(): Unit =
    doExport(FenExporter, "FEN")

  private def doFenImport(): Unit =
    doImport(FenParser, "FEN")

  private def doPgnExport(): Unit =
    doExport(PgnExporter, "PGN")

  private def doPgnImport(): Unit =
    doImport(PgnParser, "PGN")

  private def doJsonExport(): Unit =
    val fileChooser = new FileChooser {
      title = "Export Game as JSON"
      initialFileName = "chess_game.json"
      extensionFilters.add(new ExtensionFilter("JSON files (*.json)", "*.json"))
      extensionFilters.add(new ExtensionFilter("All files", "*.*"))
    }

    Option(fileChooser.showSaveDialog(stage)).foreach { selectedFile =>
      val result = FileSystemGameService.saveGameToFile(
        engine.context,
        selectedFile.toPath,
        JsonExporter,
      )
      result match
        case Right(_)  => showMessage(s"✓ Game saved to: ${selectedFile.getName}")
        case Left(err) => showMessage(s"⚠️ Error saving file: $err")
    }

  private def doJsonImport(): Unit =
    val fileChooser = new FileChooser {
      title = "Import Game from JSON"
      extensionFilters.add(new ExtensionFilter("JSON files (*.json)", "*.json"))
      extensionFilters.add(new ExtensionFilter("All files", "*.*"))
    }

    Option(fileChooser.showOpenDialog(stage)).foreach { selectedFile =>
      val result = FileSystemGameService.loadGameFromFile(
        selectedFile.toPath,
        JsonParser,
      )
      result match
        case Right(gameContext) =>
          engine.loadPosition(gameContext)
          showMessage(s"✓ Game loaded from: ${selectedFile.getName}")
        case Left(err) =>
          showMessage(s"⚠️ Error: $err")
    }

  private def doExport(exporter: GameContextExport, formatName: String): Unit = {
    val exported = exporter.exportGameContext(engine.context)
    showCopyDialog(s"$formatName Export", exported)
  }

  private def doImport(importer: GameContextImport, formatName: String): Unit =
    showInputDialog(s"$formatName Import", rows = 5).foreach { input =>
      importer.importGameContext(input) match
        case Right(gameContext) =>
          engine.loadPosition(gameContext)
          showMessage(s"✓ $formatName loaded successfully!")
        case Left(err) =>
          showMessage(s"⚠️  $formatName Error: $err")
    }

  private def showCopyDialog(title: String, content: String): Unit =
    val area = new javafx.scene.control.TextArea(content)
    area.setEditable(false)
    area.setWrapText(true)
    area.setPrefRowCount(4)
    val alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION)
    alert.setTitle(title)
    alert.setHeaderText("")
    alert.getDialogPane.setContent(area)
    alert.getDialogPane.setPrefWidth(500)
    alert.initOwner(stage.delegate)
    alert.showAndWait()

  private def showInputDialog(title: String, rows: Int = 2): Option[String] =
    val area = new javafx.scene.control.TextArea()
    area.setWrapText(true)
    area.setPrefRowCount(rows)
    val dialog = new javafx.scene.control.Dialog[String]()
    dialog.setTitle(title)
    dialog.getDialogPane.setContent(area)
    dialog.getDialogPane.getButtonTypes.addAll(
      javafx.scene.control.ButtonType.OK,
      javafx.scene.control.ButtonType.CANCEL,
    )
    dialog.setResultConverter { bt =>
      if bt == javafx.scene.control.ButtonType.OK then area.getText else ""
    }
    dialog.initOwner(stage.delegate)
    val result = dialog.showAndWait()
    if result.isPresent && result.get.nonEmpty then Some(result.get) else None
