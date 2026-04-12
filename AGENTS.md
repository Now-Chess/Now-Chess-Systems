# Project Context

This is a scala project using raw-http.

Middleware includes: custom.

High-impact files (most imported, changes here affect many other files):
- modules/api/src/main/scala/de/nowchess/api/game/GameContext.scala (imported by 50 files)
- modules/api/src/main/scala/de/nowchess/api/board/Square.scala (imported by 33 files)
- modules/api/src/main/scala/de/nowchess/api/board/Color.scala (imported by 30 files)
- modules/api/src/main/scala/de/nowchess/api/move/Move.scala (imported by 29 files)
- modules/api/src/main/scala/de/nowchess/api/board/Board.scala (imported by 19 files)
- modules/api/src/main/scala/de/nowchess/api/board/PieceType.scala (imported by 18 files)
- modules/rule/src/main/scala/de/nowchess/rules/sets/DefaultRules.scala (imported by 17 files)
- modules/api/src/main/scala/de/nowchess/api/board/Piece.scala (imported by 15 files)

Required environment variables (no defaults):
- STOCKFISH_PATH (modules/bot/python/nnue.py)

Read .codesight/wiki/index.md for orientation (WHERE things live). Then read actual source files before implementing. Wiki articles are navigation aids, not implementation guides.
Read .codesight/CODESIGHT.md for the complete AI context map including all routes, schema, components, libraries, config, middleware, and dependency graph.
