package de.nowchess.store.service

import de.nowchess.api.dto.GameWritebackEventDto
import de.nowchess.store.domain.GameRecord
import de.nowchess.store.repository.GameRecordRepository
import io.micrometer.core.instrument.{Counter, MeterRegistry, Timer}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional

import scala.compiletime.uninitialized
import java.time.Instant

@ApplicationScoped
class GameWritebackService:

  // scalafix:off DisableSyntax.var
  @Inject
  var repository: GameRecordRepository = uninitialized

  @Inject
  var meterRegistry: MeterRegistry = uninitialized
  // scalafix:on DisableSyntax.var

  private lazy val writebackTimer: Timer =
    meterRegistry.timer("nowchess.store.writeback.duration")

  private lazy val writebackSkipped: Counter =
    meterRegistry.counter("nowchess.store.writeback.skipped")

  private def gamesWrittenCounter(operation: String): Counter =
    meterRegistry.counter("nowchess.store.games.written", "operation", operation)

  @Transactional
  def writeBack(event: GameWritebackEventDto): Unit =
    writebackTimer.record(() => doWriteBack(event))

  private def doWriteBack(event: GameWritebackEventDto): Unit =
    repository.findByGameId(event.gameId) match
      case None =>
        createRecord(event)
        gamesWrittenCounter("create").increment()
      case Some(r) if event.moveCount > r.moveCount || event.pgn != r.pgn =>
        updateRecord(r, event)
        gamesWrittenCounter("update").increment()
      case _ =>
        writebackSkipped.increment()

  private def createRecord(event: GameWritebackEventDto): Unit =
    val record = new GameRecord
    record.gameId = event.gameId
    record.fen = event.fen
    record.pgn = event.pgn
    record.moveCount = event.moveCount
    record.whiteId = event.whiteId
    record.whiteName = event.whiteName
    record.blackId = event.blackId
    record.blackName = event.blackName
    record.mode = event.mode
    record.resigned = event.resigned
    applyClockFields(record, event)
    record.result = event.result.orNull
    record.terminationReason = event.terminationReason.orNull
    record.createdAt = Instant.now()
    record.updatedAt = Instant.now()
    repository.persist(record)

  private def updateRecord(r: GameRecord, event: GameWritebackEventDto): Unit =
    r.fen = event.fen
    r.pgn = event.pgn
    r.moveCount = event.moveCount
    r.whiteId = event.whiteId
    r.whiteName = event.whiteName
    r.blackId = event.blackId
    r.blackName = event.blackName
    r.mode = event.mode
    r.resigned = event.resigned
    applyClockFields(r, event)
    r.pendingDrawOffer = event.pendingDrawOffer.orNull
    r.pendingTakebackOffer = event.pendingTakebackRequest.orNull
    r.result = event.result.orNull
    r.terminationReason = event.terminationReason.orNull
    r.updatedAt = Instant.now()
    repository.merge(r)

  private def applyClockFields(r: GameRecord, event: GameWritebackEventDto): Unit =
    r.limitSeconds = event.limitSeconds.map(java.lang.Integer.valueOf).orNull
    r.incrementSeconds = event.incrementSeconds.map(java.lang.Integer.valueOf).orNull
    r.daysPerMove = event.daysPerMove.map(java.lang.Integer.valueOf).orNull
    r.whiteRemainingMs = event.whiteRemainingMs.map(java.lang.Long.valueOf).orNull
    r.blackRemainingMs = event.blackRemainingMs.map(java.lang.Long.valueOf).orNull
    r.incrementMs = event.incrementMs.map(java.lang.Long.valueOf).orNull
    r.clockLastTickAt = event.clockLastTickAt.map(java.lang.Long.valueOf).orNull
    r.clockMoveDeadline = event.clockMoveDeadline.map(java.lang.Long.valueOf).orNull
    r.clockActiveColor = event.clockActiveColor.orNull
    r.pendingDrawOffer = event.pendingDrawOffer.orNull
