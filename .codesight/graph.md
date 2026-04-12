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
