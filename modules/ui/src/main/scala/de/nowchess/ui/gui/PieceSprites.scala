package de.nowchess.ui.gui

import scalafx.scene.image.{Image, ImageView}
import de.nowchess.api.board.{Piece, PieceType, Color}

/** Utility object for loading chess piece sprites. */
object PieceSprites:
  
  private val spriteCache = scala.collection.mutable.Map[String, Image]()
  
  /** Load a piece sprite image from resources.
   *  Sprites are cached for performance.
   */
  def loadPieceImage(piece: Piece, size: Double = 60.0): ImageView =
    val key = s"${piece.color.label.toLowerCase}_${piece.pieceType.label.toLowerCase}"
    val image = spriteCache.getOrElseUpdate(key, loadImage(key))
    
    new ImageView(image) {
      fitWidth = size
      fitHeight = size
      preserveRatio = true
      smooth = true
    }
  
  private def loadImage(key: String): Image =
    val path = s"/sprites/pieces/$key.png"
    val stream = getClass.getResourceAsStream(path)
    if stream == null then
      throw new RuntimeException(s"Could not load sprite: $path")
    new Image(stream)
  
  /** Get square colors for the board using theme. */
  object SquareColors:
    val White = "#F3C8A0"  // Warm light beige
    val Black = "#BA6D4B"  // Warm terracotta
    val Selected = "#C19EF5" // Purple highlight
    val ValidMove = "#E1EAA9" // Light yellow-green
    val Border = "#5A2C28"   // Dark brown border
