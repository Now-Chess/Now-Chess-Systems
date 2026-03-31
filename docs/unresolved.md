## [2026-03-31] Unreachable code blocking 100% statement coverage

**Requirement/Bug:** Reach 100% statement coverage in core module.

**Root Cause:** 4 remaining uncovered statements (99.6% coverage) are unreachable code:
1. **PgnParser.scala:160** (`case _ => None` in extractPromotion) - Regex `=([QRBN])` only matches those 4 characters; fallback case can never execute
2. **GameHistory.scala:29** (`addMove$default$4` compiler-generated method) - Method overload 3 without defaults shadows the 4-param version, making promotionPiece default accessor unreachable
3. **GameEngine.scala:201-202** (`case _` in completePromotion) - GameController.completePromotion always returns one of 4 expected MoveResult types; catch-all is defensive code

**Attempted Fixes:**
1. Added comprehensive PGN parsing tests (all 4 promotion types) - PgnParser improved from 95.8% to 99.4%
2. Added GameHistory tests using named parameters - hit `addMove$default$3` (castleSide) but not `$default$4` (promotionPiece)
3. Named parameter approach: `addMove(from=..., to=..., promotionPiece=...)` triggers 4-param with castleSide default ✓
4. Positional approach: `addMove(f, t, None, None)` requires all 4 args (explicit, no defaults used) - doesn't hit $default$4
5. Root issue: Scala's overload resolution prefers more-specific non-default overloads (2-param, 3-param) over the 4-param with defaults

**Recommendation:** 99.6% (1029/1033) is maximum achievable without refactoring method overloads. Unreachable code design patterns:
- **Pattern 1 (unreachable regex fallback):** Defensive pattern match against exhaustive regex
- **Pattern 2 (overshadowed defaults):** Method overloads shadow default parameters in parent signature
- **Pattern 3 (defensive catch-all):** Error handling for impossible external API returns
