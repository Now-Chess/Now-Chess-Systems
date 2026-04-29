package de.nowchess.store.service

import de.nowchess.api.dto.GameWritebackEventDto
import de.nowchess.store.domain.GameRecord
import de.nowchess.store.repository.GameRecordRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional

import scala.compiletime.uninitialized
import java.time.Instant

@ApplicationScoped
class GameWritebackService:
  @Inject
  // scalafix:off DisableSyntax.var
  var repository: GameRecordRepository = uninitialized
  // scalafix:on

  @Transactional
  def writeBack(event: GameWritebackEventDto): Unit =
    repository.findByGameId(event.gameId) match
      case None =>
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
        record.limitSeconds = event.limitSeconds.map(java.lang.Integer.valueOf).orNull
        record.incrementSeconds = event.incrementSeconds.map(java.lang.Integer.valueOf).orNull
        record.daysPerMove = event.daysPerMove.map(java.lang.Integer.valueOf).orNull
        record.whiteRemainingMs = event.whiteRemainingMs.map(java.lang.Long.valueOf).orNull
        record.blackRemainingMs = event.blackRemainingMs.map(java.lang.Long.valueOf).orNull
        record.incrementMs = event.incrementMs.map(java.lang.Long.valueOf).orNull
        record.clockLastTickAt = event.clockLastTickAt.map(java.lang.Long.valueOf).orNull
        record.clockMoveDeadline = event.clockMoveDeadline.map(java.lang.Long.valueOf).orNull
        record.clockActiveColor = event.clockActiveColor.orNull
        record.pendingDrawOffer = event.pendingDrawOffer.orNull
        record.result = event.result.orNull
        record.terminationReason = event.terminationReason.orNull
        record.createdAt = Instant.now()
        record.updatedAt = Instant.now()
        repository.persist(record)
      case Some(r) if event.moveCount > r.moveCount || event.pgn != r.pgn =>
        r.fen = event.fen
        r.pgn = event.pgn
        r.moveCount = event.moveCount
        r.whiteId = event.whiteId
        r.whiteName = event.whiteName
        r.blackId = event.blackId
        r.blackName = event.blackName
        r.mode = event.mode
        r.resigned = event.resigned
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
        r.pendingTakebackOffer = event.pendingTakebackRequest.orNull
        r.result = event.result.orNull
        r.terminationReason = event.terminationReason.orNull
        r.updatedAt = Instant.now()
        repository.merge(r)
      case _ => ()
