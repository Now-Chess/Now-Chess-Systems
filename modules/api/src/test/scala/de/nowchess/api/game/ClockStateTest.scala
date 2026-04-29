package de.nowchess.api.game

import de.nowchess.api.board.Color
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import java.time.Instant
import java.time.temporal.ChronoUnit

class ClockStateTest extends AnyFunSuite with Matchers:

  private val t0  = Instant.parse("2024-01-01T00:00:00Z")
  private val t1s = t0.plusSeconds(1)
  private val t5s = t0.plusSeconds(5)

  // ── LiveClockState ────────────────────────────────────────────────────────

  test("LiveClockState.afterMove deducts elapsed and adds increment on valid move"):
    val cs = LiveClockState(300_000L, 300_000L, 3_000L, t0, Color.White)
    cs.afterMove(Color.White, t5s) match
      case Right(updated: LiveClockState) =>
        updated.whiteRemainingMs shouldBe (300_000L - 5_000L + 3_000L)
        updated.blackRemainingMs shouldBe 300_000L
        updated.activeColor shouldBe Color.Black
        updated.lastTickAt shouldBe t5s
      case other => fail(s"Expected Right(LiveClockState), got $other")

  test("LiveClockState.afterMove returns Left when time exhausted"):
    val cs = LiveClockState(2_000L, 300_000L, 0L, t0, Color.White)
    cs.afterMove(Color.White, t5s) shouldBe Left(Color.White)

  test("LiveClockState.afterMove returns Left when time exactly zero"):
    val cs = LiveClockState(5_000L, 300_000L, 0L, t0, Color.White)
    cs.afterMove(Color.White, t5s) shouldBe Left(Color.White)

  test("LiveClockState.remainingMs for active color deducts live elapsed"):
    val cs  = LiveClockState(300_000L, 300_000L, 0L, t0, Color.White)
    val now = t5s
    cs.remainingMs(Color.White, now) shouldBe (300_000L - 5_000L)

  test("LiveClockState.remainingMs for inactive color returns stored value"):
    val cs = LiveClockState(200_000L, 300_000L, 0L, t0, Color.White)
    cs.remainingMs(Color.Black, t5s) shouldBe 300_000L

  test("LiveClockState.remainingMs clamps to zero when overdue"):
    val cs = LiveClockState(1_000L, 300_000L, 0L, t0, Color.White)
    cs.remainingMs(Color.White, t5s) shouldBe 0L

  test("LiveClockState.afterMove advances activeColor to opponent"):
    val cs = LiveClockState(300_000L, 300_000L, 0L, t0, Color.Black)
    cs.afterMove(Color.Black, t1s) match
      case Right(updated: LiveClockState) => updated.activeColor shouldBe Color.White
      case other                          => fail(s"Expected Right, got $other")

  // ── CorrespondenceClockState ──────────────────────────────────────────────

  test("CorrespondenceClockState.afterMove advances deadline on valid move"):
    val deadline = t0.plus(3L, ChronoUnit.DAYS)
    val cs       = CorrespondenceClockState(deadline, 3, Color.White)
    cs.afterMove(Color.White, t1s) match
      case Right(updated: CorrespondenceClockState) =>
        updated.moveDeadline shouldBe t1s.plus(3L, ChronoUnit.DAYS)
        updated.activeColor shouldBe Color.Black
      case other => fail(s"Expected Right(CorrespondenceClockState), got $other")

  test("CorrespondenceClockState.afterMove returns Left when move is past deadline"):
    val deadline = t0.plus(1L, ChronoUnit.DAYS)
    val cs       = CorrespondenceClockState(deadline, 3, Color.White)
    val lateMove = t0.plus(2L, ChronoUnit.DAYS)
    cs.afterMove(Color.White, lateMove) shouldBe Left(Color.White)

  test("CorrespondenceClockState.remainingMs returns time until deadline"):
    val deadline = t0.plus(3L, ChronoUnit.DAYS)
    val cs       = CorrespondenceClockState(deadline, 3, Color.White)
    val expected = deadline.toEpochMilli - t1s.toEpochMilli
    cs.remainingMs(Color.White, t1s) shouldBe expected

  test("CorrespondenceClockState.remainingMs clamps to zero when overdue"):
    val deadline = t0.plus(1L, ChronoUnit.DAYS)
    val cs       = CorrespondenceClockState(deadline, 3, Color.White)
    val overdue  = t0.plus(2L, ChronoUnit.DAYS)
    cs.remainingMs(Color.White, overdue) shouldBe 0L

  // ── ClockState.fromTimeControl ────────────────────────────────────────────

  test("fromTimeControl with Clock returns LiveClockState with correct initial values"):
    ClockState.fromTimeControl(TimeControl.Clock(300, 3), Color.White, t0) match
      case Some(cs: LiveClockState) =>
        cs.whiteRemainingMs shouldBe 300_000L
        cs.blackRemainingMs shouldBe 300_000L
        cs.incrementMs shouldBe 3_000L
        cs.activeColor shouldBe Color.White
        cs.lastTickAt shouldBe t0
      case other => fail(s"Expected Some(LiveClockState), got $other")

  test("fromTimeControl with Correspondence returns CorrespondenceClockState"):
    ClockState.fromTimeControl(TimeControl.Correspondence(3), Color.White, t0) match
      case Some(cs: CorrespondenceClockState) =>
        cs.moveDeadline shouldBe t0.plus(3L, ChronoUnit.DAYS)
        cs.daysPerMove shouldBe 3
        cs.activeColor shouldBe Color.White
      case other => fail(s"Expected Some(CorrespondenceClockState), got $other")

  test("fromTimeControl with Unlimited returns None"):
    ClockState.fromTimeControl(TimeControl.Unlimited, Color.White, t0) shouldBe None

  test("fromTimeControl with Black as starting color sets activeColor correctly"):
    ClockState.fromTimeControl(TimeControl.Clock(300, 0), Color.Black, t0) match
      case Some(cs: LiveClockState) => cs.activeColor shouldBe Color.Black
      case other                    => fail(s"Expected Some(LiveClockState), got $other")
