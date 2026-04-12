package de.nowchess.api.player

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PlayerInfoTest extends AnyFunSuite with Matchers:

  test("PlayerId and PlayerInfo preserve constructor values") {
    val raw = "player-123"
    val id  = PlayerId(raw)

    id.value shouldBe raw

    val playerId = PlayerId("p1")
    val info     = PlayerInfo(playerId, "Magnus")
    info.id.value shouldBe "p1"
    info.displayName shouldBe "Magnus"
  }
