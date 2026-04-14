package de.nowchess.ui.gui

import scalafx.scene.image.{Image, ImageView}
import de.nowchess.api.board.{Color, Piece, PieceType}

/** Utility object for loading chess piece sprites. */
object PieceSprites:

  private val spriteCache = scala.collection.mutable.Map[String, Option[Image]]()

  /** Load a piece sprite image from resources. Sprites are cached for performance.
    */
  def loadPieceImage(piece: Piece, size: Double = 60.0): Option[ImageView] =
    val key = s"${piece.color.label.toLowerCase}_${piece.pieceType.label.toLowerCase}"
    spriteCache.getOrElseUpdate(key, loadImage(key)).map { image =>
      new ImageView(image) {
        fitWidth = size
        fitHeight = size
        preserveRatio = true
        smooth = true
      }
    }

  private def loadImage(key: String): Option[Image] =
    val path = s"/sprites/pieces/$key.png"
    Option(getClass.getResourceAsStream(path)).map(new Image(_))

  /** Get square colors for the board using theme. */
  object SquareColors:
    val White     = "#F3C8A0" // Warm light beige
    val Black     = "#BA6D4B" // Warm terracotta
    val Selected  = "#C19EF5" // Purple highlight
    val ValidMove = "#E1EAA9" // Light yellow-green
    val Border    = "#5A2C28" // Dark brown border
