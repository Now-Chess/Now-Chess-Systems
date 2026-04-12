# NowChessSystems — AI Context Map

> **Stack:** raw-http | none | unknown | scala

> 0 routes | 0 models | 0 components | 35 lib files | 0 env vars | 0 middleware
> **Token savings:** this file is ~3.700 tokens. Without it, AI exploration would cost ~18.200 tokens. **Saves ~14.500 tokens per conversation.**

---

# Libraries

- `jacoco-reporter/scoverage_coverage_gaps.py`
  - function parse_scoverage_xml: (xml_path) -> tuple[dict, list[ClassGap]]
  - function format_agent: (project_stats, classes) -> str
  - function format_json: (project_stats, classes) -> str
  - function format_markdown: (project_stats, classes) -> str
  - function format_module_gaps: (module_name, classes, stmt_pct) -> str
  - function run_scan_modules: (modules_dir, package_filter, min_coverage) -> None
  - _...4 more_
- `jacoco-reporter/test_gaps.py`
  - function parse_suite_xml: (xml_path) -> SuiteResult
  - function load_module: (module_dir, results_subdir) -> Optional[ModuleResult]
  - function format_module: (mod) -> str
  - function run: (modules_dir, results_subdir, module_filter) -> None
  - function main: () -> None
  - class TestCase
  - _...2 more_
- `modules/api/src/main/scala/de/nowchess/api/board/Board.scala`
  - class Board
  - function apply
  - function pieceAt
  - function updated
  - function removed
  - function withMove
  - _...2 more_
- `modules/api/src/main/scala/de/nowchess/api/board/CastlingRights.scala`
  - function hasAnyRights
  - function hasRights
  - function revokeColor
  - function revokeKingSide
  - function revokeQueenSide
  - class CastlingRights
- `modules/api/src/main/scala/de/nowchess/api/board/Color.scala` — function opposite, function label
- `modules/api/src/main/scala/de/nowchess/api/board/Piece.scala` — class Piece
- `modules/api/src/main/scala/de/nowchess/api/board/PieceType.scala` — function label
- `modules/api/src/main/scala/de/nowchess/api/board/Square.scala`
  - class Square
  - function fromAlgebraic
  - function offset
- `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`
  - function withBoard
  - function withTurn
  - function withCastlingRights
  - function withEnPassantSquare
  - function withHalfMoveClock
  - function withMove
  - _...2 more_
- `modules/api/src/main/scala/de/nowchess/api/player/PlayerInfo.scala` — class PlayerId, function apply
- `modules/api/src/main/scala/de/nowchess/api/response/ApiResponse.scala`
  - class ApiResponse
  - function error
  - function totalPages
- `modules/core/src/main/scala/de/nowchess/chess/command/Command.scala`
  - class Command
  - function execute
  - function undo
  - function description
  - class MoveResult
- `modules/core/src/main/scala/de/nowchess/chess/command/CommandInvoker.scala`
  - class CommandInvoker
  - function execute
  - function undo
  - function redo
  - function history
  - function getCurrentIndex
  - _...3 more_
- `modules/core/src/main/scala/de/nowchess/chess/controller/Parser.scala` — class Parser, function parseMove
- `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`
  - class GameEngine
  - function isPendingPromotion
  - function board
  - function turn
  - function context
  - function canUndo
  - _...10 more_
- `modules/core/src/main/scala/de/nowchess/chess/observer/Observer.scala`
  - function context
  - class Observer
  - function onGameEvent
  - class Observable
  - function subscribe
  - function unsubscribe
  - _...1 more_
- `modules/io/src/main/scala/de/nowchess/io/GameContextExport.scala` — class GameContextExport, function exportGameContext
- `modules/io/src/main/scala/de/nowchess/io/GameContextImport.scala` — class GameContextImport, function importGameContext
- `modules/io/src/main/scala/de/nowchess/io/fen/FenExporter.scala`
  - class FenExporter
  - function boardToFen
  - function gameContextToFen
  - function exportGameContext
- `modules/io/src/main/scala/de/nowchess/io/fen/FenParser.scala`
  - class FenParser
  - function parseFen
  - function importGameContext
  - function parseBoard
- `modules/io/src/main/scala/de/nowchess/io/fen/FenParserCombinators.scala`
  - class FenParserCombinators
  - function parseFen
  - function parseBoard
  - function importGameContext
- `modules/io/src/main/scala/de/nowchess/io/fen/FenParserFastParse.scala`
  - class FenParserFastParse
  - function parseFen
  - function parseBoard
  - function importGameContext
- `modules/io/src/main/scala/de/nowchess/io/fen/FenParserSupport.scala` — function buildSquares
- `modules/io/src/main/scala/de/nowchess/io/pgn/PgnExporter.scala`
  - class PgnExporter
  - function exportGameContext
  - function exportGame
- `modules/io/src/main/scala/de/nowchess/io/pgn/PgnParser.scala`
  - class PgnParser
  - function validatePgn
  - function importGameContext
  - function parsePgn
  - function parseAlgebraicMove
- `modules/rule/src/main/scala/de/nowchess/rules/RuleSet.scala`
  - class RuleSet
  - function candidateMoves
  - function legalMoves
  - function allLegalMoves
  - function isCheck
  - function isCheckmate
  - _...4 more_
- `modules/rule/src/main/scala/de/nowchess/rules/sets/DefaultRules.scala`
  - class DefaultRules
  - function loop
  - function toMoves
  - function loop
- `modules/ui/src/main/scala/de/nowchess/ui/Main.scala` — class Main, function main
- `modules/ui/src/main/scala/de/nowchess/ui/gui/ChessBoardView.scala`
  - class ChessBoardView
  - function updateBoard
  - function updateUndoRedoButtons
  - function showMessage
  - function showPromotionDialog
- `modules/ui/src/main/scala/de/nowchess/ui/gui/ChessGUI.scala`
  - class ChessGUIApp
  - class ChessGUILauncher
  - function getEngine
  - function launch
- `modules/ui/src/main/scala/de/nowchess/ui/gui/GUIObserver.scala` — class GUIObserver
- `modules/ui/src/main/scala/de/nowchess/ui/gui/PieceSprites.scala`
  - class PieceSprites
  - function loadPieceImage
  - class SquareColors
- `modules/ui/src/main/scala/de/nowchess/ui/terminal/TerminalUI.scala` — class TerminalUI, function start
- `modules/ui/src/main/scala/de/nowchess/ui/utils/PieceUnicode.scala` — function unicode
- `modules/ui/src/main/scala/de/nowchess/ui/utils/Renderer.scala` — class Renderer, function render

---

# Dependency Graph

## Most Imported Files (change these carefully)

- `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala` — imported by **28** files
- `modules/api/src/main/scala/de/nowchess/api/board/Square.scala` — imported by **21** files
- `modules/api/src/main/scala/de/nowchess/api/board/Color.scala` — imported by **19** files
- `modules/api/src/main/scala/de/nowchess/api/move/Move.scala` — imported by **14** files
- `modules/api/src/main/scala/de/nowchess/api/board/Board.scala` — imported by **13** files
- `modules/api/src/main/scala/de/nowchess/api/board/Piece.scala` — imported by **10** files
- `modules/api/src/main/scala/de/nowchess/api/board/PieceType.scala` — imported by **9** files
- `modules/io/src/main/scala/de/nowchess/io/fen/FenParser.scala` — imported by **9** files
- `modules/rule/src/main/scala/de/nowchess/rules/sets/DefaultRules.scala` — imported by **8** files
- `modules/io/src/main/scala/de/nowchess/io/GameContextImport.scala` — imported by **7** files
- `modules/api/src/main/scala/de/nowchess/api/board/CastlingRights.scala` — imported by **4** files
- `modules/io/src/main/scala/de/nowchess/io/GameContextExport.scala` — imported by **4** files
- `modules/rule/src/main/scala/de/nowchess/rules/RuleSet.scala` — imported by **4** files
- `modules/core/src/main/scala/de/nowchess/chess/observer/Observer.scala` — imported by **4** files
- `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala` — imported by **4** files
- `modules/io/src/main/scala/de/nowchess/io/pgn/PgnParser.scala` — imported by **2** files
- `modules/io/src/main/scala/de/nowchess/io/pgn/PgnExporter.scala` — imported by **2** files
- `modules/io/src/main/scala/de/nowchess/io/fen/FenExporter.scala` — imported by **2** files
- `modules/io/src/main/scala/de/nowchess/io/fen/FenParserSupport.scala` — imported by **2** files
- `modules/core/src/main/scala/de/nowchess/chess/controller/Parser.scala` — imported by **1** files

## Import Map (who imports what)

- `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala` ← `modules/core/src/main/scala/de/nowchess/chess/command/Command.scala`, `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/main/scala/de/nowchess/chess/observer/Observer.scala`, `modules/core/src/test/scala/de/nowchess/chess/command/CommandInvokerBranchTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/command/CommandInvokerTest.scala` +23 more
- `modules/api/src/main/scala/de/nowchess/api/board/Square.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/main/scala/de/nowchess/api/move/Move.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/api/src/test/scala/de/nowchess/api/move/MoveTest.scala`, `modules/core/src/main/scala/de/nowchess/chess/command/Command.scala` +16 more
- `modules/api/src/main/scala/de/nowchess/api/board/Color.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/main/scala/de/nowchess/chess/observer/Observer.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/EngineTestHelpers.scala` +14 more
- `modules/api/src/main/scala/de/nowchess/api/move/Move.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/test/scala/de/nowchess/api/board/BoardTest.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineIntegrationTest.scala` +9 more
- `modules/api/src/main/scala/de/nowchess/api/board/Board.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/EngineTestHelpers.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineGameEndingTest.scala` +8 more
- `modules/api/src/main/scala/de/nowchess/api/board/Piece.scala` ← `modules/core/src/main/scala/de/nowchess/chess/command/Command.scala`, `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEnginePromotionTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineScenarioTest.scala`, `modules/rule/src/test/scala/de/nowchess/rule/DefaultRulesStateTransitionsTest.scala` +5 more
- `modules/api/src/main/scala/de/nowchess/api/board/PieceType.scala` ← `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineIntegrationTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEnginePromotionTest.scala`, `modules/rule/src/test/scala/de/nowchess/rule/DefaultRulesStateTransitionsTest.scala`, `modules/rule/src/test/scala/de/nowchess/rule/DefaultRulesTest.scala` +4 more
- `modules/io/src/main/scala/de/nowchess/io/fen/FenParser.scala` ← `modules/core/src/test/scala/de/nowchess/chess/engine/EngineTestHelpers.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineLoadGameTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineNotationTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEnginePromotionTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineScenarioTest.scala` +4 more
- `modules/rule/src/main/scala/de/nowchess/rules/sets/DefaultRules.scala` ← `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/EngineTestHelpers.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineIntegrationTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEnginePromotionTest.scala`, `modules/io/src/main/scala/de/nowchess/io/pgn/PgnExporter.scala` +3 more
- `modules/io/src/main/scala/de/nowchess/io/GameContextImport.scala` ← `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineIntegrationTest.scala`, `modules/io/src/main/scala/de/nowchess/io/fen/FenParser.scala`, `modules/io/src/main/scala/de/nowchess/io/fen/FenParserCombinators.scala`, `modules/io/src/main/scala/de/nowchess/io/fen/FenParserFastParse.scala` +2 more

---

_Generated by [codesight](https://github.com/Houseofmvps/codesight) — see your codebase clearly_