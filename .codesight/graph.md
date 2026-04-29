# Dependency Graph

## Most Imported Files (change these carefully)

- `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala` — imported by **76** files
- `modules/api/src/main/scala/de/nowchess/api/board/Square.scala` — imported by **57** files
- `modules/api/src/main/scala/de/nowchess/api/move/Move.scala` — imported by **55** files
- `modules/api/src/main/scala/de/nowchess/api/board/Color.scala` — imported by **47** files
- `modules/rule/src/main/scala/de/nowchess/rules/sets/DefaultRules.scala` — imported by **28** files
- `modules/api/src/main/scala/de/nowchess/api/board/Board.scala` — imported by **20** files
- `modules/api/src/main/scala/de/nowchess/api/board/PieceType.scala` — imported by **20** files
- `modules/api/src/main/scala/de/nowchess/api/board/Piece.scala` — imported by **20** files
- `modules/api/src/main/scala/de/nowchess/api/game/DrawReason.scala` — imported by **18** files
- `modules/api/src/main/scala/de/nowchess/api/game/GameResult.scala` — imported by **18** files
- `modules/api/src/main/scala/de/nowchess/api/rules/RuleSet.scala` — imported by **14** files
- `modules/api/src/main/scala/de/nowchess/api/board/CastlingRights.scala` — imported by **13** files
- `modules/io/src/main/scala/de/nowchess/io/fen/FenParser.scala` — imported by **11** files
- `modules/api/src/main/scala/de/nowchess/api/player/PlayerInfo.scala` — imported by **9** files
- `modules/api/src/main/scala/de/nowchess/api/error/GameError.scala` — imported by **9** files
- `modules/core/src/main/scala/de/nowchess/chess/observer/Observer.scala` — imported by **9** files
- `modules/api/src/main/scala/de/nowchess/api/io/GameContextImport.scala` — imported by **8** files
- `modules/core/src/main/scala/de/nowchess/chess/grpc/IoGrpcClientWrapper.scala` — imported by **7** files
- `modules/api/src/main/scala/de/nowchess/api/game/GameMode.scala` — imported by **6** files
- `modules/api/src/main/scala/de/nowchess/api/bot/Bot.scala` — imported by **6** files

## Import Map (who imports what)

- `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala` ← `modules/api/src/main/scala/de/nowchess/api/bot/Bot.scala`, `modules/api/src/main/scala/de/nowchess/api/io/GameContextExport.scala`, `modules/api/src/main/scala/de/nowchess/api/io/GameContextImport.scala`, `modules/api/src/main/scala/de/nowchess/api/rules/RuleSet.scala`, `modules/bot/src/main/scala/de/nowchess/bot/BotMoveRepetition.scala` +71 more
- `modules/api/src/main/scala/de/nowchess/api/board/Square.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/main/scala/de/nowchess/api/move/Move.scala`, `modules/api/src/main/scala/de/nowchess/api/rules/RuleSet.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/api/src/test/scala/de/nowchess/api/move/MoveTest.scala` +52 more
- `modules/api/src/main/scala/de/nowchess/api/move/Move.scala` ← `modules/api/src/main/scala/de/nowchess/api/bot/Bot.scala`, `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/main/scala/de/nowchess/api/rules/RuleSet.scala`, `modules/api/src/test/scala/de/nowchess/api/board/BoardTest.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala` +50 more
- `modules/api/src/main/scala/de/nowchess/api/board/Color.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/ClockState.scala`, `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/main/scala/de/nowchess/api/game/GameResult.scala`, `modules/api/src/test/scala/de/nowchess/api/game/ClockStateTest.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala` +42 more
- `modules/rule/src/main/scala/de/nowchess/rules/sets/DefaultRules.scala` ← `modules/bot/src/main/scala/de/nowchess/bot/bots/ClassicalBot.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/HybridBot.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/NNUEBot.scala`, `modules/bot/src/main/scala/de/nowchess/bot/logic/AlphaBetaSearch.scala`, `modules/bot/src/test/scala/de/nowchess/bot/AlphaBetaSearchTest.scala` +23 more
- `modules/api/src/main/scala/de/nowchess/api/board/Board.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/nnue/NNUE.scala`, `modules/bot/src/main/scala/de/nowchess/bot/logic/MoveOrdering.scala`, `modules/bot/src/test/scala/de/nowchess/bot/AlphaBetaSearchTest.scala` +15 more
- `modules/api/src/main/scala/de/nowchess/api/board/PieceType.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/classic/EvaluationClassic.scala`, `modules/bot/src/main/scala/de/nowchess/bot/bots/nnue/NNUE.scala`, `modules/bot/src/main/scala/de/nowchess/bot/logic/AlphaBetaSearch.scala`, `modules/bot/src/main/scala/de/nowchess/bot/logic/MoveOrdering.scala` +15 more
- `modules/api/src/main/scala/de/nowchess/api/board/Piece.scala` ← `modules/bot/src/main/scala/de/nowchess/bot/bots/nnue/NNUE.scala`, `modules/bot/src/main/scala/de/nowchess/bot/logic/MoveOrdering.scala`, `modules/bot/src/main/scala/de/nowchess/bot/util/PolyglotHash.scala`, `modules/bot/src/main/scala/de/nowchess/bot/util/ZobristHash.scala`, `modules/bot/src/test/scala/de/nowchess/bot/AlphaBetaSearchTest.scala` +15 more
- `modules/api/src/main/scala/de/nowchess/api/game/DrawReason.scala` ← `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/core/src/main/scala/de/nowchess/chess/config/NativeReflectionConfig.scala`, `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/main/scala/de/nowchess/chess/grpc/CoreProtoMapper.scala`, `modules/core/src/main/scala/de/nowchess/chess/observer/Observer.scala` +13 more
- `modules/api/src/main/scala/de/nowchess/api/game/GameResult.scala` ← `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/core/src/main/scala/de/nowchess/chess/config/NativeReflectionConfig.scala`, `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/main/scala/de/nowchess/chess/grpc/CoreProtoMapper.scala`, `modules/core/src/main/scala/de/nowchess/chess/resource/GameDtoMapper.scala` +13 more
