package de.nowchess.io

import de.nowchess.api.error.GameError
import de.nowchess.api.game.GameContext
import de.nowchess.api.io.{GameContextExport, GameContextImport}

import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets
import scala.util.Try

/** Service for persisting and loading game states to/from disk.
  *
  * Abstracts file I/O operations away from the UI layer. Handles both reading and writing game files.
  */
trait GameFileService:
  def saveGameToFile(context: GameContext, path: Path, exporter: GameContextExport): Either[GameError, Unit]
  def loadGameFromFile(path: Path, importer: GameContextImport): Either[GameError, GameContext]

/** Default implementation using the file system. */
object FileSystemGameService extends GameFileService:

  /** Save a game context to a file using the specified exporter. */
  def saveGameToFile(context: GameContext, path: Path, exporter: GameContextExport): Either[GameError, Unit] =
    Try {
      val json = exporter.exportGameContext(context)
      Files.write(path, json.getBytes(StandardCharsets.UTF_8))
      ()
    }.fold(
      ex => Left(GameError.FileWriteError(s"Failed to save file: ${ex.getMessage}")),
      _ => Right(()),
    )

  /** Load a game context from a file using the specified importer. */
  def loadGameFromFile(path: Path, importer: GameContextImport): Either[GameError, GameContext] =
    Try {
      val json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
      importer.importGameContext(json)
    }.fold(
      ex => Left(GameError.FileReadError(s"Failed to load file: ${ex.getMessage}")),
      result => result,
    )
