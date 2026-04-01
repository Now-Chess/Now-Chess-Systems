package de.nowchess.ui.gui

import scalafx.Includes.*
import scalafx.application.Platform
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.{Button, ButtonType, ChoiceDialog, Label}
import scalafx.scene.layout.{BorderPane, GridPane, HBox, VBox, StackPane}
import scalafx.scene.paint.Color as FXColor
import scalafx.scene.shape.Rectangle
import scalafx.scene.text.{Font, Text}
import scalafx.stage.Stage
import de.nowchess.api.board.{Board, Color, Piece, PieceType, Square, File, Rank}
import de.nowchess.api.game.{CastlingRights, GameState, GameStatus}
import de.nowchess.api.move.PromotionPiece
import de.nowchess.chess.engine.GameEngine
import de.nowchess.chess.logic.{CastlingRightsCalculator, EnPassantCalculator, GameHistory, GameRules, withCastle}
import de.nowchess.chess.notation.{FenExporter, FenParser, PgnExporter, PgnParser}

/** ScalaFX chess board view that displays the game state.
 *  Uses chess sprites and color palette.
 *  Handles user interactions (clicks) and sends moves to GameEngine.
 */
class ChessBoardView(val stage: Stage, private val engine: GameEngine) extends BorderPane:
  
  private val squareSize = 70.0
  private val comicSansFontFamily = "Comic Sans MS"
  private val boardGrid = new GridPane()
  private val messageLabel = new Label {
    text = "Welcome!"
    font = Font.font(comicSansFontFamily, 16)
    padding = Insets(10)
  }
  
  private var currentBoard: Board = engine.board
  private var currentTurn: Color = engine.turn
  private var selectedSquare: Option[Square] = None
  private val squareViews = scala.collection.mutable.Map[(Int, Int), StackPane]()
  
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
      messageLabel
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
          new Button("Undo") {
            font = Font.font(comicSansFontFamily, 12)
            onAction = _ => if engine.canUndo then engine.undo()
            style = "-fx-background-radius: 8; -fx-background-color: #B9DAD1;"
          },
          new Button("Redo") {
            font = Font.font(comicSansFontFamily, 12)
            onAction = _ => if engine.canRedo then engine.redo()
            style = "-fx-background-radius: 8; -fx-background-color: #B9C2DA;"
          },
          new Button("Reset") {
            font = Font.font(comicSansFontFamily, 12)
            onAction = _ => engine.reset()
            style = "-fx-background-radius: 8; -fx-background-color: #E1EAA9;"
          }
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
          }
        )
      }
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
    
    updateBoard(currentBoard, currentTurn)
  
  private def createSquare(rank: Int, file: Int): StackPane =
    val isWhite = (rank + file) % 2 == 0
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
    if engine.isPendingPromotion then
      return // Don't allow moves during promotion
    
    val clickedSquare = Square(File.values(file), Rank.values(rank))
    
    selectedSquare match
      case None =>
        // First click - select piece if it belongs to current player
        currentBoard.pieceAt(clickedSquare).foreach { piece =>
          if piece.color == currentTurn then
            selectedSquare = Some(clickedSquare)
            highlightSquare(rank, file, PieceSprites.SquareColors.Selected)
            val legalDests = GameRules.legalMoves(currentBoard, engine.history, currentTurn)
              .collect { case (`clickedSquare`, to) => to }
            legalDests.foreach { sq =>
              highlightSquare(sq.rank.ordinal, sq.file.ordinal, PieceSprites.SquareColors.ValidMove)
            }
        }
      
      case Some(fromSquare) =>
        // Second click - attempt move
        if clickedSquare == fromSquare then
          // Deselect
          selectedSquare = None
          updateBoard(currentBoard, currentTurn)
        else
          // Try to move
          val moveStr = s"${fromSquare}$clickedSquare"
          engine.processUserInput(moveStr)
          selectedSquare = None
  
  def updateBoard(board: Board, turn: Color): Unit =
    currentBoard = board
    currentTurn = turn
    selectedSquare = None
    
    // Update all squares
    for
      rank <- 0 until 8
      file <- 0 until 8
    do
      squareViews.get((rank, file)).foreach { stackPane =>
        val isWhite = (rank + file) % 2 == 0
        val baseColor = if isWhite then PieceSprites.SquareColors.White else PieceSprites.SquareColors.Black
        
        val bgRect = new Rectangle {
          width = squareSize
          height = squareSize
          fill = FXColor.web(baseColor)
          arcWidth = 8
          arcHeight = 8
        }
        
        val square = Square(File.values(file), Rank.values(rank))
        val pieceOption = board.pieceAt(square)
        
        val children = pieceOption match
          case Some(piece) =>
            Seq(bgRect, PieceSprites.loadPieceImage(piece, squareSize * 0.8))
          case None =>
            Seq(bgRect)
        
        stackPane.children = children
      }
  
  private def highlightSquare(rank: Int, file: Int, color: String): Unit =
    squareViews.get((rank, file)).foreach { stackPane =>
      val bgRect = new Rectangle {
        width = squareSize
        height = squareSize
        fill = FXColor.web(color)
        arcWidth = 8
        arcHeight = 8
      }
      
      val square = Square(File.values(file), Rank.values(rank))
      val pieceOption = currentBoard.pieceAt(square)
      
      stackPane.children = pieceOption match
        case Some(piece) =>
          Seq(bgRect, PieceSprites.loadPieceImage(piece, squareSize * 0.8))
        case None =>
          Seq(bgRect)
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
    
    val result = dialog.showAndWait()
    result match
      case Some("Queen") => engine.completePromotion(PromotionPiece.Queen)
      case Some("Rook") => engine.completePromotion(PromotionPiece.Rook)
      case Some("Bishop") => engine.completePromotion(PromotionPiece.Bishop)
      case Some("Knight") => engine.completePromotion(PromotionPiece.Knight)
      case _ => engine.completePromotion(PromotionPiece.Queen) // Default

  private def doFenExport(): Unit =
    val state = GameState(
      piecePlacement  = FenExporter.boardToFen(currentBoard),
      activeColor     = currentTurn,
      castlingWhite   = CastlingRightsCalculator.deriveCastlingRights(engine.history, Color.White),
      castlingBlack   = CastlingRightsCalculator.deriveCastlingRights(engine.history, Color.Black),
      enPassantTarget = EnPassantCalculator.enPassantTarget(currentBoard, engine.history),
      halfMoveClock   = 0,
      fullMoveNumber  = engine.history.moves.size / 2 + 1,
      status          = GameStatus.InProgress
    )
    showCopyDialog("FEN Export", FenExporter.gameStateToFen(state))

  private def doFenImport(): Unit =
    showInputDialog("FEN Import", rows = 1).foreach { fen =>
      FenParser.parseFen(fen) match
        case None => showMessage("Invalid FEN")
        case Some(state) =>
          FenParser.parseBoard(state.piecePlacement) match
            case None => showMessage("Invalid FEN board")
            case Some(board) => engine.loadPosition(board, GameHistory.empty, state.activeColor)
    }

  private def doPgnExport(): Unit =
    showCopyDialog("PGN Export", PgnExporter.exportGame(Map.empty, engine.history))

  private def doPgnImport(): Unit =
    showInputDialog("PGN Import", rows = 6).foreach { pgn =>
      PgnParser.parsePgn(pgn) match
        case None => showMessage("Invalid PGN")
        case Some(pgnGame) =>
          val (finalBoard, finalHistory) = pgnGame.moves.foldLeft((Board.initial, GameHistory.empty)):
            case ((board, history), move) =>
              val color = if history.moves.size % 2 == 0 then Color.White else Color.Black
              val newBoard = move.castleSide match
                case Some(side) => board.withCastle(color, side)
                case None =>
                  val (b, _) = board.withMove(move.from, move.to)
                  move.promotionPiece match
                    case Some(pp) =>
                      val pt = pp match
                        case PromotionPiece.Queen  => PieceType.Queen
                        case PromotionPiece.Rook   => PieceType.Rook
                        case PromotionPiece.Bishop => PieceType.Bishop
                        case PromotionPiece.Knight => PieceType.Knight
                      b.updated(move.to, Piece(color, pt))
                    case None => b
              (newBoard, history.addMove(move))
          val finalTurn = if finalHistory.moves.size % 2 == 0 then Color.White else Color.Black
          engine.loadPosition(finalBoard, finalHistory, finalTurn)
    }

  private def showCopyDialog(title: String, content: String): Unit =
    val area = new javafx.scene.control.TextArea(content)
    area.setEditable(false)
    area.setWrapText(true)
    area.setPrefRowCount(4)
    val alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION)
    alert.setTitle(title)
    alert.setHeaderText(null)
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
      javafx.scene.control.ButtonType.CANCEL
    )
    dialog.setResultConverter { bt =>
      if bt == javafx.scene.control.ButtonType.OK then area.getText else null
    }
    dialog.initOwner(stage.delegate)
    val result = dialog.showAndWait()
    if result.isPresent && result.get != null && result.get.nonEmpty then Some(result.get) else None
