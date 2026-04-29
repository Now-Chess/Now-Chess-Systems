package de.nowchess.chess.engine

import de.nowchess.api.board.Color
import de.nowchess.api.game.{
  ClockState,
  CorrespondenceClockState,
  DrawReason,
  GameResult,
  LiveClockState,
  TimeControl,
  WinReason,
}
import de.nowchess.chess.observer.*
import de.nowchess.rules.sets.DefaultRules
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.time.temporal.ChronoUnit

class GameEngineClockTest extends AnyFunSuite with Matchers:

  private def makeClockEngine(tc: TimeControl): GameEngine =
    new GameEngine(ruleSet = DefaultRules, timeControl = tc)

  // ── Unlimited ─────────────────────────────────────────────────────────────

  test("Unlimited time control: no clock state"):
    val engine = makeClockEngine(TimeControl.Unlimited)
    engine.currentClockState shouldBe None

  // ── Live clock initialisation ─────────────────────────────────────────────

  test("Clock(300,3) initialises both sides to 300,000ms"):
    val engine = makeClockEngine(TimeControl.Clock(300, 3))
    engine.currentClockState match
      case Some(cs: LiveClockState) =>
        cs.whiteRemainingMs shouldBe 300_000L
        cs.blackRemainingMs shouldBe 300_000L
        cs.incrementMs shouldBe 3_000L
        cs.activeColor shouldBe Color.White
      case other => fail(s"Expected Some(LiveClockState), got $other")

  // ── Clock advances after move ─────────────────────────────────────────────

  test("After White move, activeColor flips to Black and white time decreases"):
    val engine = makeClockEngine(TimeControl.Clock(300, 3))
    engine.processUserInput("e2e4")
    engine.currentClockState match
      case Some(cs: LiveClockState) =>
        cs.activeColor shouldBe Color.Black
        cs.whiteRemainingMs should be < 300_000L + 3_000L
        cs.blackRemainingMs shouldBe 300_000L
      case other => fail(s"Expected Some(LiveClockState), got $other")

  // ── Time flag via injection ───────────────────────────────────────────────

  test("TimeFlagEvent fires and result is Win(opponent) when White flags on move"):
    val engine   = makeClockEngine(TimeControl.Clock(300, 0))
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)
    // Inject nearly-exhausted clock: White has 1ms, will flag on move
    val expiredClock = LiveClockState(1L, 300_000L, 0L, Instant.now().minusSeconds(10), Color.White)
    engine.injectClockState(Some(expiredClock))

    engine.processUserInput("e2e4")

    observer.hasEvent[TimeFlagEvent] shouldBe true
    observer.getEvent[TimeFlagEvent].map(_.flaggedColor) shouldBe Some(Color.White)
    engine.context.result shouldBe Some(GameResult.Win(Color.Black, WinReason.TimeControl))

  test("TimeFlagEvent fires and result is Win(Black) when Black flags on move"):
    val engine   = makeClockEngine(TimeControl.Clock(300, 0))
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)
    engine.processUserInput("e2e4")
    observer.clear()

    val expiredClock = LiveClockState(300_000L, 1L, 0L, Instant.now().minusSeconds(10), Color.Black)
    engine.injectClockState(Some(expiredClock))

    engine.processUserInput("e7e5")

    observer.hasEvent[TimeFlagEvent] shouldBe true
    observer.getEvent[TimeFlagEvent].map(_.flaggedColor) shouldBe Some(Color.Black)
    engine.context.result shouldBe Some(GameResult.Win(Color.White, WinReason.TimeControl))

  test("Flag with insufficient material gives Draw(InsufficientMaterial)"):
    // King vs King — White flags but Black can't mate
    // White king e4, Black king e6: e4d3 is a legal move (not adjacent to e6)
    val engine   = makeClockEngine(TimeControl.Clock(300, 0))
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)
    EngineTestHelpers.loadFen(engine, "8/8/4k3/8/4K3/8/8/8 w - - 0 1")
    observer.clear()

    val expiredClock = LiveClockState(1L, 300_000L, 0L, Instant.now().minusSeconds(10), Color.White)
    engine.injectClockState(Some(expiredClock))

    engine.processUserInput("e4d3")

    observer.hasEvent[TimeFlagEvent] shouldBe true
    engine.context.result shouldBe Some(GameResult.Draw(DrawReason.InsufficientMaterial))

  // ── Correspondence clock ──────────────────────────────────────────────────

  test("Correspondence(3): after move, deadline is ~3 days from move time"):
    val engine = makeClockEngine(TimeControl.Correspondence(3))
    val before = Instant.now()
    engine.processUserInput("e2e4")
    val after = Instant.now()
    engine.currentClockState match
      case Some(cs: CorrespondenceClockState) =>
        val expectedMin = before.plus(3L, ChronoUnit.DAYS)
        val expectedMax = after.plus(3L, ChronoUnit.DAYS)
        cs.moveDeadline.isAfter(expectedMin.minusSeconds(1)) shouldBe true
        cs.moveDeadline.isBefore(expectedMax.plusSeconds(1)) shouldBe true
        cs.activeColor shouldBe Color.Black
      case other => fail(s"Expected Some(CorrespondenceClockState), got $other")

  test("Correspondence flag fires TimeFlagEvent when move past deadline"):
    val engine   = makeClockEngine(TimeControl.Correspondence(3))
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)
    // Inject expired deadline
    val expired = CorrespondenceClockState(Instant.now().minusSeconds(60), 3, Color.White)
    engine.injectClockState(Some(expired))

    engine.processUserInput("e2e4")

    observer.hasEvent[TimeFlagEvent] shouldBe true
    observer.getEvent[TimeFlagEvent].map(_.flaggedColor) shouldBe Some(Color.White)

  // ── reset() restarts clock ────────────────────────────────────────────────

  test("reset() restarts clock to full time"):
    val engine = makeClockEngine(TimeControl.Clock(300, 3))
    engine.processUserInput("e2e4")
    engine.reset()
    engine.currentClockState match
      case Some(cs: LiveClockState) =>
        cs.whiteRemainingMs shouldBe 300_000L
        cs.blackRemainingMs shouldBe 300_000L
        cs.activeColor shouldBe Color.White
      case other => fail(s"Expected Some(LiveClockState), got $other")

  // ── Passive expiry via scheduler ──────────────────────────────────────────

  test("Scheduler fires TimeFlagEvent when active player's clock expires passively"):
    // Scheduler starts on engine creation, so TimeFlagEvent fires without a move being made
    val engine   = new GameEngine(ruleSet = DefaultRules, timeControl = TimeControl.Clock(1, 0))
    val observer = new EngineTestHelpers.MockObserver()
    engine.subscribe(observer)
    Thread.sleep(1500)
    observer.hasEvent[TimeFlagEvent] shouldBe true
