package de.nowchess.store.domain

import io.quarkus.hibernate.orm.panache.PanacheEntityBase
import jakarta.persistence.*
import scala.compiletime.uninitialized
import java.time.Instant

@Entity
@Table(
  name = "game_records",
  indexes = Array(
    new Index(name = "idx_game_records_white_id", columnList = "whiteId"),
    new Index(name = "idx_game_records_black_id", columnList = "blackId"),
  ),
)
class GameRecord extends PanacheEntityBase:
  // scalafix:off DisableSyntax.var
  @Id
  @Column(nullable = false)
  var gameId: String = uninitialized

  @Column(nullable = false, columnDefinition = "TEXT")
  var fen: String = uninitialized

  @Column(nullable = false, columnDefinition = "TEXT")
  var pgn: String = uninitialized

  @Column(nullable = false)
  var moveCount: Int = 0

  @Column(nullable = false)
  var createdAt: Instant = uninitialized

  @Column(nullable = false)
  var updatedAt: Instant = uninitialized

  // Player info
  @Column(nullable = false)
  var whiteId: String = uninitialized

  @Column(nullable = false)
  var whiteName: String = uninitialized

  @Column(nullable = false)
  var blackId: String = uninitialized

  @Column(nullable = false)
  var blackName: String = uninitialized

  @Column(nullable = false)
  var mode: String = uninitialized

  // Time control
  @Column
  var limitSeconds: java.lang.Integer = uninitialized

  @Column
  var incrementSeconds: java.lang.Integer = uninitialized

  @Column
  var daysPerMove: java.lang.Integer = uninitialized

  // Clock state
  @Column
  var whiteRemainingMs: java.lang.Long = uninitialized

  @Column
  var blackRemainingMs: java.lang.Long = uninitialized

  @Column
  var incrementMs: java.lang.Long = uninitialized

  @Column
  var clockLastTickAt: java.lang.Long = uninitialized

  @Column
  var clockMoveDeadline: java.lang.Long = uninitialized

  @Column
  var clockActiveColor: String = uninitialized

  // Game meta
  @Column(nullable = false)
  var resigned: Boolean = false

  @Column
  var pendingDrawOffer: String = uninitialized

  @Column
  var pendingTakebackOffer: String = uninitialized

  // Game result
  @Column
  var result: String = uninitialized

  @Column
  var terminationReason: String = uninitialized
  // scalafix:on
