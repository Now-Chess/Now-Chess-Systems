package de.nowchess.io

import de.nowchess.api.game.GameContext
import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.api.move.Move
import de.nowchess.io.json.{JsonExporter, JsonParser}
import java.nio.file.{Files, Paths}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.util.Using

class GameFileServiceSuite extends AnyFunSuite with Matchers:

  test("saveGameToFile: writes JSON file successfully") {
    val tmpFile = Files.createTempFile("chess_test_", ".json")
    try
      val context = GameContext.initial
      val result  = FileSystemGameService.saveGameToFile(context, tmpFile, JsonExporter)

      assert(result.isRight)
      assert(Files.exists(tmpFile))
      assert(Files.size(tmpFile) > 0)
    finally Files.deleteIfExists(tmpFile)
  }

  test("loadGameFromFile: reads JSON file successfully") {
    val tmpFile = Files.createTempFile("chess_test_", ".json")
    try
      val originalContext = GameContext.initial

      // Save
      FileSystemGameService.saveGameToFile(originalContext, tmpFile, JsonExporter)

      // Load
      val result = FileSystemGameService.loadGameFromFile(tmpFile, JsonParser)

      assert(result.isRight)
      val loaded = result.getOrElse(GameContext.initial)
      assert(loaded == originalContext)
    finally Files.deleteIfExists(tmpFile)
  }

  test("loadGameFromFile: returns error on missing file") {
    val nonExistentFile = Paths.get("/tmp/nonexistent_chess_game_file_12345.json")
    val result          = FileSystemGameService.loadGameFromFile(nonExistentFile, JsonParser)

    assert(result.isLeft)
  }

  test("saveGameToFile: persists game with moves") {
    val tmpFile = Files.createTempFile("chess_test_moves_", ".json")
    try
      val move1 = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4))
      val move2 = Move(Square(File.E, Rank.R7), Square(File.E, Rank.R5))
      val context = GameContext.initial
        .withMove(move1)
        .withMove(move2)

      val saveResult = FileSystemGameService.saveGameToFile(context, tmpFile, JsonExporter)
      assert(saveResult.isRight)

      val loadResult = FileSystemGameService.loadGameFromFile(tmpFile, JsonParser)
      assert(loadResult.isRight)
      val loaded = loadResult.getOrElse(GameContext.initial)
      assert(loaded.moves.length == 2)
    finally Files.deleteIfExists(tmpFile)
  }

  test("saveGameToFile: overwrites existing file") {
    val tmpFile = Files.createTempFile("chess_test_overwrite_", ".json")
    try
      // Write first file
      val context1 = GameContext.initial
      FileSystemGameService.saveGameToFile(context1, tmpFile, JsonExporter)
      val size1 = Files.size(tmpFile)

      // Write second file (should overwrite)
      val move     = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4))
      val context2 = GameContext.initial.withMove(move)
      FileSystemGameService.saveGameToFile(context2, tmpFile, JsonExporter)

      val loadResult = FileSystemGameService.loadGameFromFile(tmpFile, JsonParser)
      assert(loadResult.isRight)
      val loaded = loadResult.getOrElse(GameContext.initial)
      assert(loaded.moves.length == 1)
    finally Files.deleteIfExists(tmpFile)
  }

  test("loadGameFromFile: handles invalid JSON in file") {
    val tmpFile = Files.createTempFile("chess_test_invalid_", ".json")
    try
      Files.write(tmpFile, "{ invalid json}".getBytes())
      val result = FileSystemGameService.loadGameFromFile(tmpFile, JsonParser)

      assert(result.isLeft)
    finally Files.deleteIfExists(tmpFile)
  }

  test("round-trip: save and load preserves game state") {
    val tmpFile = Files.createTempFile("chess_test_roundtrip_", ".json")
    try
      val move1 = Move(Square(File.A, Rank.R2), Square(File.A, Rank.R4))
      val move2 = Move(Square(File.H, Rank.R7), Square(File.H, Rank.R5))
      val original = GameContext.initial
        .withMove(move1)
        .withMove(move2)
        .withHalfMoveClock(3)

      FileSystemGameService.saveGameToFile(original, tmpFile, JsonExporter)
      val loadResult = FileSystemGameService.loadGameFromFile(tmpFile, JsonParser)

      assert(loadResult.isRight)
      val loaded = loadResult.getOrElse(GameContext.initial)
      assert(loaded.moves.length == 2)
      assert(loaded.halfMoveClock == 3)
    finally Files.deleteIfExists(tmpFile)
  }

  test("saveGameToFile: handles exporter that throws exception") {
    val tmpFile = Files.createTempFile("chess_test_exporter_error_", ".json")
    try
      val context = GameContext.initial
      val faultyExporter = new GameContextExport {
        def exportGameContext(c: GameContext): String =
          sys.error("Export failed")
      }

      val result = FileSystemGameService.saveGameToFile(context, tmpFile, faultyExporter)
      assert(result.isLeft)
      assert(result.left.toOption.get.contains("Failed to save file"))
    finally Files.deleteIfExists(tmpFile)
  }
