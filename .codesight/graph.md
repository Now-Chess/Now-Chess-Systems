# Dependency Graph

## Most Imported Files (change these carefully)

- `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala` — imported by **60** files
- `modules/api/src/main/scala/de/nowchess/api/move/Move.scala` — imported by **40** files
- `modules/api/src/main/scala/de/nowchess/api/board/Square.scala` — imported by **39** files
- `modules/api/src/main/scala/de/nowchess/api/board/Color.scala` — imported by **36** files
- `modules/api/src/main/scala/de/nowchess/api/board/Board.scala` — imported by **22** files
- `modules/api/src/main/scala/de/nowchess/api/board/PieceType.scala` — imported by **21** files
- `modules/api/src/main/scala/de/nowchess/api/board/Piece.scala` — imported by **21** files
- `modules/rule/src/main/scala/de/nowchess/rules/sets/DefaultRules.scala` — imported by **17** files
- `modules/rule/src/main/scala/de/nowchess/rules/RuleSet.scala` — imported by **10** files
- `modules/io/src/main/scala/de/nowchess/io/fen/FenParser.scala` — imported by **10** files
- `modules/api/src/main/scala/de/nowchess/api/board/CastlingRights.scala` — imported by **8** files
- `modules/io/src/main/scala/de/nowchess/io/GameContextImport.scala` — imported by **8** files
- `modules/bot/src/main/scala/de/nowchess/bot/util/PolyglotBook.scala` — imported by **5** files
- `modules/bot/src/main/scala/de/nowchess/bot/BotDifficulty.scala` — imported by **5** files
- `modules/io/src/main/scala/de/nowchess/io/GameContextExport.scala` — imported by **5** files
- `modules/bot/src/main/scala/de/nowchess/bot/bots/ClassicalBot.scala` — imported by **4** files
- `modules/bot/src/main/scala/de/nowchess/bot/bots/classic/EvaluationClassic.scala` — imported by **4** files
- `modules/bot/src/main/scala/de/nowchess/bot/logic/AlphaBetaSearch.scala` — imported by **4** files
- `modules/bot/src/main/scala/de/nowchess/bot/Bot.scala` — imported by **4** files
- `modules/core/src/main/scala/de/nowchess/chess/observer/Observer.scala` — imported by **4** files

## Import Map (who imports what)

- `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala` ← `modules/bot/src/main/scala/de/nowchess/bot/Bot.scala`, `modules/bot/src/main/scala/de/nowchess/bot/BotMoveRepetition.scala`, `modules/bot/src/main/scala/de/nowchess/bot/ai/Evaluation.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/ClassicalBot.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/HybridBot.scala` +55 more
- `modules/api/src/main/scala/de/nowchess/api/move/Move.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/test/scala/de/nowchess/api/board/BoardTest.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/bot/src/main/scala/de/nowchess/bot/Bot.scala`, `modules/bot/src/main/scala/de/nowchess/bot/BotMoveRepetition.scala` +35 more
- `modules/api/src/main/scala/de/nowchess/api/board/Square.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/main/scala/de/nowchess/api/move/Move.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/api/src/test/scala/de/nowchess/api/move/MoveTest.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/classic/EvaluationClassic.scala` +34 more
- `modules/api/src/main/scala/de/nowchess/api/board/Color.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/classic/EvaluationClassic.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/nnue/NNUE.scala`, `modules/bot/src/main/scala/de/nowchess/bot/logic/MoveOrdering.scala` +31 more
- `modules/api/src/main/scala/de/nowchess/api/board/Board.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/nnue/NNUE.scala`, `modules/bot/src/main/scala/de/nowchess/bot/logic/MoveOrdering.scala`, `modules/bot/src/test/scala/de/nowchess/bot/AlphaBetaSearchTest.scala` +17 more
- `modules/api/src/main/scala/de/nowchess/api/board/PieceType.scala` ← `modules/bot/src/main/scala/de/nowchess/bot/bots/classic/EvaluationClassic.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/nnue/NNUE.scala`, `modules/bot/src/main/scala/de/nowchess/bot/logic/AlphaBetaSearch.scala`, `modules/bot/src/main/scala/de/nowchess/bot/logic/MoveOrdering.scala`, `modules/bot/src/main/scala/de/nowchess/bot/util/PolyglotHash.scala` +16 more
- `modules/api/src/main/scala/de/nowchess/api/board/Piece.scala` ← `modules/bot/src/main/scala/de/nowchess/bot/bots/nnue/NNUE.scala`, `modules/bot/src/main/scala/de/nowchess/bot/logic/MoveOrdering.scala`, `modules/bot/src/main/scala/de/nowchess/bot/util/PolyglotHash.scala`, `modules/bot/src/main/scala/de/nowchess/bot/util/ZobristHash.scala`, `modules/bot/src/test/scala/de/nowchess/bot/AlphaBetaSearchTest.scala` +16 more
- `modules/rule/src/main/scala/de/nowchess/rules/sets/DefaultRules.scala` ← `modules/bot/src/main/scala/de/nowchess/bot/bots/ClassicalBot.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/HybridBot.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/NNUEBot.scala`, `modules/bot/src/main/scala/de/nowchess/bot/logic/AlphaBetaSearch.scala`, `modules/bot/src/test/scala/de/nowchess/bot/AlphaBetaSearchTest.scala` +12 more
- `modules/rule/src/main/scala/de/nowchess/rules/RuleSet.scala` ← `modules/bot/src/main/scala/de/nowchess/bot/bots/ClassicalBot.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/HybridBot.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/NNUEBot.scala`, `modules/bot/src/main/scala/de/nowchess/bot/logic/AlphaBetaSearch.scala`, `modules/bot/src/test/scala/de/nowchess/bot/AlphaBetaSearchTest.scala` +5 more
- `modules/io/src/main/scala/de/nowchess/io/fen/FenParser.scala` ← `modules/bot/src/test/scala/de/nowchess/bot/PolyglotHashTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/EngineTestHelpers.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineLoadGameTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineNotationTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEnginePromotionTest.scala` +5 more
