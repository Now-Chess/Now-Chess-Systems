package de.nowchess.api.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class EventEnvelopeTest extends AnyFunSuite with Matchers:

  private val mapper =
    val m = new ObjectMapper()
    m.registerModule(DefaultScalaModule)
    m.findAndRegisterModules()
    m

  test("EventEnvelope round-trips through JSON") {
    val payload = mapper.createObjectNode()
    payload.put("gameId", "game-123")
    payload.put("difficulty", 3)

    val original = EventEnvelope.of(EventType.GameStart, payload, Some("corr-abc"))

    val json    = mapper.writeValueAsString(original)
    val decoded = mapper.readValue(json, classOf[EventEnvelope])

    decoded.eventId shouldBe original.eventId
    decoded.`type` shouldBe original.`type`
    decoded.payload shouldBe original.payload
    decoded.timestamp shouldBe original.timestamp
    decoded.correlationId shouldBe Some("corr-abc")
  }

  test("EventEnvelope serializes without correlationId") {
    val payload = mapper.createObjectNode()
    payload.put("challengeId", "ch-1")

    val envelope = EventEnvelope.of(EventType.ChallengeCreated, payload)
    val json     = mapper.writeValueAsString(envelope)
    val decoded  = mapper.readValue(json, classOf[EventEnvelope])

    decoded.`type` shouldBe EventType.ChallengeCreated
    decoded.correlationId shouldBe None
  }

  test("EventEnvelope.of generates unique eventIds") {
    val payload = mapper.createObjectNode()
    val e1      = EventEnvelope.of(EventType.BotGameStart, payload)
    val e2      = EventEnvelope.of(EventType.BotGameStart, payload)
    e1.eventId should not equal e2.eventId
  }
