# Dependency Graph

## Most Imported Files (change these carefully)

- `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala` — imported by **76** files
- `modules/api/src/main/scala/de/nowchess/api/board/Square.scala` — imported by **56** files
- `modules/api/src/main/scala/de/nowchess/api/move/Move.scala` — imported by **54** files
- `modules/api/src/main/scala/de/nowchess/api/board/Color.scala` — imported by **47** files
- `modules/rule/src/main/scala/de/nowchess/rules/sets/DefaultRules.scala` — imported by **27** files
- `modules/api/src/main/scala/de/nowchess/api/board/PieceType.scala` — imported by **20** files
- `modules/api/src/main/scala/de/nowchess/api/game/DrawReason.scala` — imported by **20** files
- `modules/api/src/main/scala/de/nowchess/api/game/GameResult.scala` — imported by **20** files
- `modules/api/src/main/scala/de/nowchess/api/board/Board.scala` — imported by **19** files
- `modules/api/src/main/scala/de/nowchess/api/board/Piece.scala` — imported by **19** files
- `modules/api/src/main/scala/de/nowchess/api/rules/RuleSet.scala` — imported by **14** files
- `modules/api/src/main/scala/de/nowchess/api/board/CastlingRights.scala` — imported by **12** files
- `modules/io/src/main/scala/de/nowchess/io/fen/FenParser.scala` — imported by **12** files
- `modules/api/src/main/scala/de/nowchess/api/error/GameError.scala` — imported by **9** files
- `modules/core/src/main/scala/de/nowchess/chess/observer/Observer.scala` — imported by **9** files
- `modules/account/src/main/scala/de/nowchess/account/config/RedisConfig.scala` — imported by **8** files
- `modules/api/src/main/scala/de/nowchess/api/io/GameContextImport.scala` — imported by **8** files
- `modules/core/src/main/scala/de/nowchess/chess/grpc/IoGrpcClientWrapper.scala` — imported by **8** files
- `modules/api/src/main/scala/de/nowchess/api/player/PlayerInfo.scala` — imported by **6** files
- `modules/api/src/main/scala/de/nowchess/api/game/GameMode.scala` — imported by **6** files

## Import Map (who imports what)

- `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala` ← `modules/api/src/main/scala/de/nowchess/api/grpc/ProtoMapperBase.scala`, `modules/api/src/main/scala/de/nowchess/api/io/GameContextExport.scala`, `modules/api/src/main/scala/de/nowchess/api/io/GameContextImport.scala`, `modules/api/src/main/scala/de/nowchess/api/rules/RuleSet.scala`, `modules/core/src/main/scala/de/nowchess/chess/adapter/RuleSetRestAdapter.scala` +71 more
- `modules/api/src/main/scala/de/nowchess/api/board/Square.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/main/scala/de/nowchess/api/move/Move.scala`, `modules/api/src/main/scala/de/nowchess/api/rules/RuleSet.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/api/src/test/scala/de/nowchess/api/move/MoveTest.scala` +51 more
- `modules/api/src/main/scala/de/nowchess/api/move/Move.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/main/scala/de/nowchess/api/rules/RuleSet.scala`, `modules/api/src/test/scala/de/nowchess/api/board/BoardTest.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/core/src/main/scala/de/nowchess/chess/adapter/RuleSetRestAdapter.scala` +49 more
- `modules/api/src/main/scala/de/nowchess/api/board/Color.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/ClockState.scala`, `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/main/scala/de/nowchess/api/game/GameResult.scala`, `modules/api/src/test/scala/de/nowchess/api/game/ClockStateTest.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala` +42 more
- `modules/rule/src/main/scala/de/nowchess/rules/sets/DefaultRules.scala` ← `modules/core/src/test/scala/de/nowchess/chess/engine/EngineTestHelpers.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineClockTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineDrawOfferTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineGameEndingTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineIntegrationTest.scala` +22 more
- `modules/api/src/main/scala/de/nowchess/api/board/PieceType.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/core/src/main/scala/de/nowchess/chess/config/NativeReflectionConfig.scala`, `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineIntegrationTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEnginePromotionTest.scala` +15 more
- `modules/api/src/main/scala/de/nowchess/api/game/DrawReason.scala` ← `modules/api/src/main/scala/de/nowchess/api/grpc/ProtoMapperBase.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/core/src/main/scala/de/nowchess/chess/config/NativeReflectionConfig.scala`, `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/main/scala/de/nowchess/chess/grpc/CoreProtoMapper.scala` +15 more
- `modules/api/src/main/scala/de/nowchess/api/game/GameResult.scala` ← `modules/api/src/main/scala/de/nowchess/api/grpc/ProtoMapperBase.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/core/src/main/scala/de/nowchess/chess/config/NativeReflectionConfig.scala`, `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/main/scala/de/nowchess/chess/grpc/CoreProtoMapper.scala` +15 more
- `modules/api/src/main/scala/de/nowchess/api/board/Board.scala` ← `modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala`, `modules/api/src/test/scala/de/nowchess/api/game/GameContextTest.scala`, `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/EngineTestHelpers.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineIntegrationTest.scala` +14 more
- `modules/api/src/main/scala/de/nowchess/api/board/Piece.scala` ← `modules/core/src/main/scala/de/nowchess/chess/config/NativeReflectionConfig.scala`, `modules/core/src/main/scala/de/nowchess/chess/engine/GameEngine.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEnginePromotionTest.scala`, `modules/core/src/test/scala/de/nowchess/chess/engine/GameEngineScenarioTest.scala`, `modules/io/src/main/scala/de/nowchess/io/service/config/NativeReflectionConfig.scala` +14 more
