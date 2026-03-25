package de.nowchess.api.player

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PlayerInfoTest extends AnyFunSuite with Matchers:

  test("PlayerId.apply wraps a string") {
    val id = PlayerId("player-123")
    id.value shouldBe "player-123"
  }

  test("PlayerId.value unwraps to original string") {
    val raw = "abc-456"
    PlayerId(raw).value shouldBe raw
  }

  test("PlayerInfo holds id and displayName") {
    val id   = PlayerId("p1")
    val info = PlayerInfo(id, "Magnus")
    info.id.value      shouldBe "p1"
    info.displayName   shouldBe "Magnus"
  }
