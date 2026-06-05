package de.nowchess.api.event

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

final case class EventEnvelope(
    eventId: UUID,
    `type`: EventType,
    payload: JsonNode,
    timestamp: Instant,
    correlationId: Option[String],
)

object EventEnvelope:
  def of(
      `type`: EventType,
      payload: JsonNode,
      correlationId: Option[String] = None,
  ): EventEnvelope =
    EventEnvelope(
      eventId = UUID.randomUUID(),
      `type` = `type`,
      payload = payload,
      timestamp = Instant.now(),
      correlationId = correlationId,
    )
