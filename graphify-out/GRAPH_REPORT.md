# Graph Report - .  (2026-04-12)

## Corpus Check
- 78 files · ~273,497 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 480 nodes · 549 edges · 74 communities detected
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## God Nodes (most connected - your core abstractions)
1. `DefaultRules` - 35 edges
2. `GameEngine` - 29 edges
3. `ChessBoardView` - 17 edges
4. `FenParserFastParse` - 17 edges
5. `FenParserCombinators` - 16 edges
6. `PgnParser` - 14 edges
7. `FenParser` - 9 edges
8. `CommandInvoker` - 9 edges
9. `GameContext` - 8 edges
10. `FenExporter` - 7 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Communities

### Community 0 - "Community 0"
Cohesion: 0.11
Nodes (2): CastlingMove, DefaultRules

### Community 1 - "Community 1"
Cohesion: 0.09
Nodes (17): ClassGap, _compact_ranges(), _find_scoverage_xml(), format_agent(), format_json(), format_markdown(), format_module_gaps(), main() (+9 more)

### Community 2 - "Community 2"
Cohesion: 0.11
Nodes (2): GameEngine, PendingPromotion

### Community 3 - "Community 3"
Cohesion: 0.09
Nodes (4): FenParserCombinators, EmptyToken, FenParserSupport, PieceToken

### Community 4 - "Community 4"
Cohesion: 0.14
Nodes (9): format_module(), load_module(), main(), ModuleResult, parse_suite_xml(), run(), SuiteResult, TestCase (+1 more)

### Community 5 - "Community 5"
Cohesion: 0.2
Nodes (1): ChessBoardView

### Community 6 - "Community 6"
Cohesion: 0.15
Nodes (1): FenParserFastParse

### Community 7 - "Community 7"
Cohesion: 0.12
Nodes (7): InvalidFormat, InvalidMove, MoveCommand, MoveResult, QuitCommand, ResetCommand, Successful

### Community 8 - "Community 8"
Cohesion: 0.12
Nodes (12): BoardResetEvent, CheckDetectedEvent, CheckmateEvent, DrawClaimedEvent, FiftyMoveRuleAvailableEvent, InvalidMoveEvent, MoveExecutedEvent, MoveRedoneEvent (+4 more)

### Community 9 - "Community 9"
Cohesion: 0.26
Nodes (2): PgnGame, PgnParser

### Community 10 - "Community 10"
Cohesion: 0.15
Nodes (3): candidateMoves(), GameEngineIntegrationTest, legalMoves()

### Community 11 - "Community 11"
Cohesion: 0.14
Nodes (1): GameEnginePromotionTest

### Community 12 - "Community 12"
Cohesion: 0.15
Nodes (2): EngineTestHelpers, MockObserver

### Community 13 - "Community 13"
Cohesion: 0.17
Nodes (3): CommandInvokerBranchTest, ConditionalFailCommand, FailingCommand

### Community 14 - "Community 14"
Cohesion: 0.36
Nodes (1): FenParser

### Community 15 - "Community 15"
Cohesion: 0.22
Nodes (1): CommandInvoker

### Community 16 - "Community 16"
Cohesion: 0.31
Nodes (5): applyMove(), Board, removed(), updated(), withMove()

### Community 17 - "Community 17"
Cohesion: 0.22
Nodes (1): GameContext

### Community 18 - "Community 18"
Cohesion: 0.25
Nodes (6): ApiError, ApiResponse, Failure, PagedResponse, Pagination, Success

### Community 19 - "Community 19"
Cohesion: 0.43
Nodes (1): FenExporter

### Community 20 - "Community 20"
Cohesion: 0.29
Nodes (1): CastlingRights

### Community 21 - "Community 21"
Cohesion: 0.4
Nodes (2): ChessGUIApp, ChessGUILauncher

### Community 22 - "Community 22"
Cohesion: 0.5
Nodes (2): offset(), Square

### Community 23 - "Community 23"
Cohesion: 0.4
Nodes (2): PlayerId, PlayerInfo

### Community 24 - "Community 24"
Cohesion: 0.5
Nodes (2): PieceSprites, SquareColors

### Community 25 - "Community 25"
Cohesion: 0.6
Nodes (1): TerminalUI

### Community 26 - "Community 26"
Cohesion: 0.6
Nodes (1): PgnExporter

### Community 27 - "Community 27"
Cohesion: 0.67
Nodes (1): GUIObserver

### Community 28 - "Community 28"
Cohesion: 0.5
Nodes (1): DefaultRulesStateTransitionsTest

### Community 29 - "Community 29"
Cohesion: 0.5
Nodes (2): EndingMockObserver, GameEngineGameEndingTest

### Community 30 - "Community 30"
Cohesion: 0.5
Nodes (2): GameEngineLoadGameTest, MockObserver

### Community 31 - "Community 31"
Cohesion: 0.5
Nodes (1): CommandInvokerTest

### Community 32 - "Community 32"
Cohesion: 0.67
Nodes (1): Parser

### Community 33 - "Community 33"
Cohesion: 0.67
Nodes (0): 

### Community 34 - "Community 34"
Cohesion: 0.67
Nodes (1): Main

### Community 35 - "Community 35"
Cohesion: 0.67
Nodes (1): Renderer

### Community 36 - "Community 36"
Cohesion: 0.67
Nodes (1): PgnExporterTest

### Community 37 - "Community 37"
Cohesion: 0.67
Nodes (1): FenExporterTest

### Community 38 - "Community 38"
Cohesion: 0.67
Nodes (1): GameEngineNotationTest

### Community 39 - "Community 39"
Cohesion: 0.67
Nodes (1): MoveCommandTest

### Community 40 - "Community 40"
Cohesion: 1.0
Nodes (1): PieceTest

### Community 41 - "Community 41"
Cohesion: 1.0
Nodes (1): PieceTypeTest

### Community 42 - "Community 42"
Cohesion: 1.0
Nodes (1): SquareTest

### Community 43 - "Community 43"
Cohesion: 1.0
Nodes (1): CastlingRightsTest

### Community 44 - "Community 44"
Cohesion: 1.0
Nodes (1): BoardTest

### Community 45 - "Community 45"
Cohesion: 1.0
Nodes (1): ColorTest

### Community 46 - "Community 46"
Cohesion: 1.0
Nodes (1): MoveTest

### Community 47 - "Community 47"
Cohesion: 1.0
Nodes (1): GameContextTest

### Community 48 - "Community 48"
Cohesion: 1.0
Nodes (1): ApiResponseTest

### Community 49 - "Community 49"
Cohesion: 1.0
Nodes (1): PlayerInfoTest

### Community 50 - "Community 50"
Cohesion: 1.0
Nodes (0): 

### Community 51 - "Community 51"
Cohesion: 1.0
Nodes (1): Piece

### Community 52 - "Community 52"
Cohesion: 1.0
Nodes (1): Move

### Community 53 - "Community 53"
Cohesion: 1.0
Nodes (1): RendererAndUnicodeTest

### Community 54 - "Community 54"
Cohesion: 1.0
Nodes (0): 

### Community 55 - "Community 55"
Cohesion: 1.0
Nodes (1): DefaultRulesTest

### Community 56 - "Community 56"
Cohesion: 1.0
Nodes (1): PgnParserTest

### Community 57 - "Community 57"
Cohesion: 1.0
Nodes (1): PgnValidatorTest

### Community 58 - "Community 58"
Cohesion: 1.0
Nodes (1): FenParserCombinatorsTest

### Community 59 - "Community 59"
Cohesion: 1.0
Nodes (1): FenParserTest

### Community 60 - "Community 60"
Cohesion: 1.0
Nodes (1): FenParserFastParseTest

### Community 61 - "Community 61"
Cohesion: 1.0
Nodes (1): ParserTest

### Community 62 - "Community 62"
Cohesion: 1.0
Nodes (1): GameEngineOutcomesTest

### Community 63 - "Community 63"
Cohesion: 1.0
Nodes (1): GameEngineSpecialMovesTest

### Community 64 - "Community 64"
Cohesion: 1.0
Nodes (1): GameEngineScenarioTest

### Community 65 - "Community 65"
Cohesion: 1.0
Nodes (1): CommandTest

### Community 66 - "Community 66"
Cohesion: 1.0
Nodes (0): 

### Community 67 - "Community 67"
Cohesion: 1.0
Nodes (0): 

### Community 68 - "Community 68"
Cohesion: 1.0
Nodes (0): 

### Community 69 - "Community 69"
Cohesion: 1.0
Nodes (0): 

### Community 70 - "Community 70"
Cohesion: 1.0
Nodes (0): 

### Community 71 - "Community 71"
Cohesion: 1.0
Nodes (1): Strip the package prefix from the full method path.

### Community 72 - "Community 72"
Cohesion: 1.0
Nodes (1): Lines that are branch points and have at least one uncovered branch statement.

### Community 73 - "Community 73"
Cohesion: 1.0
Nodes (0): 

## Knowledge Gaps
- **55 isolated node(s):** `PieceTest`, `PieceTypeTest`, `SquareTest`, `CastlingRightsTest`, `BoardTest` (+50 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 40`** (2 nodes): `PieceTest.scala`, `PieceTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 41`** (2 nodes): `PieceTypeTest.scala`, `PieceTypeTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 42`** (2 nodes): `SquareTest.scala`, `SquareTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 43`** (2 nodes): `CastlingRightsTest.scala`, `CastlingRightsTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 44`** (2 nodes): `BoardTest.scala`, `BoardTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 45`** (2 nodes): `ColorTest.scala`, `ColorTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 46`** (2 nodes): `MoveTest.scala`, `MoveTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 47`** (2 nodes): `GameContextTest.scala`, `GameContextTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 48`** (2 nodes): `ApiResponseTest.scala`, `ApiResponseTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 49`** (2 nodes): `PlayerInfoTest.scala`, `PlayerInfoTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 50`** (2 nodes): `PieceType.scala`, `label()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 51`** (2 nodes): `Piece.scala`, `Piece`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 52`** (2 nodes): `Move.scala`, `Move`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 53`** (2 nodes): `RendererAndUnicodeTest.scala`, `RendererAndUnicodeTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 54`** (2 nodes): `PieceUnicode.scala`, `unicode()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 55`** (2 nodes): `DefaultRulesTest.scala`, `DefaultRulesTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 56`** (2 nodes): `PgnParserTest.scala`, `PgnParserTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 57`** (2 nodes): `PgnValidatorTest.scala`, `PgnValidatorTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 58`** (2 nodes): `FenParserCombinatorsTest.scala`, `FenParserCombinatorsTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 59`** (2 nodes): `FenParserTest.scala`, `FenParserTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 60`** (2 nodes): `FenParserFastParseTest.scala`, `FenParserFastParseTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 61`** (2 nodes): `ParserTest.scala`, `ParserTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 62`** (2 nodes): `GameEngineOutcomesTest.scala`, `GameEngineOutcomesTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 63`** (2 nodes): `GameEngineSpecialMovesTest.scala`, `GameEngineSpecialMovesTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 64`** (2 nodes): `GameEngineScenarioTest.scala`, `GameEngineScenarioTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 65`** (2 nodes): `CommandTest.scala`, `CommandTest`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 66`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 67`** (1 nodes): `settings.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 68`** (1 nodes): `RuleSet.scala`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 69`** (1 nodes): `GameContextImport.scala`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 70`** (1 nodes): `GameContextExport.scala`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 71`** (1 nodes): `Strip the package prefix from the full method path.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 72`** (1 nodes): `Lines that are branch points and have at least one uncovered branch statement.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 73`** (1 nodes): `test_counter.py`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `FenParserFastParse` connect `Community 6` to `Community 3`?**
  _High betweenness centrality (0.004) - this node is a cross-community bridge._
- **What connects `PieceTest`, `PieceTypeTest`, `SquareTest` to the rest of the system?**
  _55 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.09 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._
- **Should `Community 3` be split into smaller, more focused modules?**
  _Cohesion score 0.09 - nodes in this community are weakly interconnected._
- **Should `Community 4` be split into smaller, more focused modules?**
  _Cohesion score 0.14 - nodes in this community are weakly interconnected._