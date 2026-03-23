## [2026-03-23] Task 6 – 6 GameControllerTest failures caused by test bugs

**Requirement / Bug:**
Task 6 required all 24 `GameControllerTest` tests to pass after wiring `GameRules.gameStatus` into
`processMove` and filling in the `gameLoop` stub branches. The implementation is correct; 6 tests
fail due to bugs in the tests themselves (committed in 13ac90a).

**Root Cause (if known):**

### Bug 1 – "legal capture returns Moved with the captured piece" (line 49-59)
The test board contains only `WhitePawn@e5` and `BlackPawn@d6` — no kings. After White plays e5d6
(capturing the Black pawn), Black has zero pieces. `GameRules.legalMoves(board, Black)` returns an
empty set and `isInCheck` returns `false` (no Black king to find), so `gameStatus` returns
`PositionStatus.Drawn`, and `processMove` correctly returns `MoveResult.Stalemate`. The test
expects `MoveResult.Moved`.
Fix: add a Black king on a safe square (e.g. `BlackKing@h1`) to the test board.

### Bug 2 – "legal move that delivers check returns MovedInCheck" (line 114-124)
The test board is `WhiteRook@a1, WhiteKing@a3, BlackKing@h8`. The intended move is `a1a8`.
However, the White King sits on a3 — square on file A between a1 and a8 — blocking the rook's
path. `MoveValidator.isLegal` correctly returns `false`, so `processMove` returns
`MoveResult.IllegalMove` rather than `MovedInCheck`. The test expects `MovedInCheck`.
Fix: move the White King off file A (e.g. `WhiteKing@c3`).

### Bug 3 – Four gameLoop output-capture tests (lines 153-199) always get empty output
`captureOutput` wraps `scala.Console.out` with a `ByteArrayOutputStream`. Inside it, `withInput`
is called, which itself calls `scala.Console.withOut(System.out)(block)`, overriding the captured
stream with the real stdout. The block therefore prints to `System.out`, not the
`ByteArrayOutputStream`, so `output` is always `""` and every `should include(...)` assertion
fails.
Fix: rewrite `withInput` to not reset `Console.out`, e.g.:
```scala
private def withInput(input: String)(block: => Unit): Unit =
  val stream = ByteArrayInputStream(input.getBytes("UTF-8"))
  scala.Console.withIn(stream)(block)
```

**Attempted Fixes:**
1. Confirmed the `GameController.scala` implementation matches the task spec exactly.
2. Verified failures via XML test report (`build/test-results/test/TEST-*.xml`).
3. Cannot modify test files per agent role constraints.

**Suggested Next Step:**
A test-writer agent (or human engineer) should:
- Apply the three fixes described above to `GameControllerTest.scala`.
- Re-run `./gradlew :modules:core:test` to confirm all 24 pass.
