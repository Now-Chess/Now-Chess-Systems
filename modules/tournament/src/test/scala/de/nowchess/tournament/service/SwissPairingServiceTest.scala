package de.nowchess.tournament.service

import de.nowchess.tournament.domain.{TournamentPairing, TournamentParticipant}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SwissPairingServiceTest:

  private def makeParticipant(
      botId: String,
      botName: String,
      points: Double = 0.0,
      byeCount: Int = 0,
  ): TournamentParticipant =
    val p = new TournamentParticipant()
    p.botId = botId
    p.botName = botName
    p.points = points
    p.byeCount = byeCount
    p

  @Test
  def pairs2PlayersRandomlyAssignsColors(): Unit =
    val p1           = makeParticipant("b1", "BotOne")
    val p2           = makeParticipant("b2", "BotTwo")
    val (pairs, bye) = SwissPairingService.computePairings(List(p1, p2), Nil)
    assertEquals(1, pairs.size)
    assertTrue(bye.isEmpty)
    val (white, black) = pairs.head
    val ids            = Set(white.botId, black.botId)
    assertEquals(Set("b1", "b2"), ids)

  @Test
  def pairs4PlayersTopVsEachOther(): Unit =
    val p1           = makeParticipant("b1", "A", points = 2.0)
    val p2           = makeParticipant("b2", "B", points = 1.5)
    val p3           = makeParticipant("b3", "C", points = 1.0)
    val p4           = makeParticipant("b4", "D", points = 0.0)
    val (pairs, bye) = SwissPairingService.computePairings(List(p1, p2, p3, p4), Nil)
    assertEquals(2, pairs.size)
    assertTrue(bye.isEmpty)
    val pair1Ids = Set(pairs(0)._1.botId, pairs(0)._2.botId)
    val pair2Ids = Set(pairs(1)._1.botId, pairs(1)._2.botId)
    assertEquals(Set("b1", "b2"), pair1Ids)
    assertEquals(Set("b3", "b4"), pair2Ids)

  @Test
  def oddCountLowestRankedGetsBye(): Unit =
    val p1           = makeParticipant("b1", "A", points = 2.0)
    val p2           = makeParticipant("b2", "B", points = 1.0)
    val p3           = makeParticipant("b3", "C", points = 0.0)
    val (pairs, bye) = SwissPairingService.computePairings(List(p1, p2, p3), Nil)
    assertEquals(1, pairs.size)
    assertTrue(bye.isDefined)
    assertEquals("b3", bye.get.botId)

  @Test
  def avoidsRematchSwapsWhenPairAlreadyPlayed(): Unit =
    val p1          = makeParticipant("b1", "A", points = 2.0)
    val p2          = makeParticipant("b2", "B", points = 1.5)
    val p3          = makeParticipant("b3", "C", points = 1.5)
    val p4          = makeParticipant("b4", "D", points = 0.0)
    val pastPairing = new TournamentPairing()
    pastPairing.whiteId = "b1"
    pastPairing.blackId = "b2"
    val (pairs, _) = SwissPairingService.computePairings(List(p1, p2, p3, p4), List(pastPairing))
    val pair1Ids   = Set(pairs(0)._1.botId, pairs(0)._2.botId)
    assertFalse(pair1Ids == Set("b1", "b2"), "b1 and b2 should not be paired again")

  @Test
  def playerWithFewerByesGetsTheByeFirst(): Unit =
    val p1           = makeParticipant("b1", "A", points = 1.0, byeCount = 1)
    val p2           = makeParticipant("b2", "B", points = 0.5, byeCount = 0)
    val p3           = makeParticipant("b3", "C", points = 0.0, byeCount = 0)
    val (pairs, bye) = SwissPairingService.computePairings(List(p1, p2, p3), Nil)
    assertEquals(1, pairs.size)
    assertTrue(bye.isDefined)
    assertEquals("b3", bye.get.botId)
