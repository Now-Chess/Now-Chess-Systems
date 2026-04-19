package de.nowchess.bot.logic

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move

final case class Window(alpha: Int, beta: Int)

final case class LoopAcc(bestMove: Option[Move], bestScore: Int, a: Int)

final case class SearchParams(
    context: GameContext,
    depth: Int,
    ply: Int,
    window: Window,
    state: SearchState,
    excludedRootMoves: Set[Move],
)

final case class SearchState(hash: Long, repetitions: Map[Long, Int]):
  def advance(nextHash: Long): SearchState =
    SearchState(
      nextHash,
      repetitions.updatedWith(nextHash) {
        case Some(v) => Some(v + 1)
        case None    => Some(1)
      },
    )

enum TTFlag:
  case Exact // Score is exact
  case Lower // Score is a lower bound
  case Upper // Score is an upper bound

final case class TTEntry(
    hash: Long,
    depth: Int,
    score: Int,
    flag: TTFlag,
    bestMove: Option[Move],
)

final class TranspositionTable(val sizePow2: Int = 20):
  private val size                          = 1 << sizePow2
  private val mask                          = size - 1L
  private val locks                         = Array.fill(size)(new Object())
  private val table: Array[Option[TTEntry]] = Array.fill(size)(None)

  def probe(hash: Long): Option[TTEntry] =
    val index = (hash & mask).toInt
    locks(index).synchronized {
      table(index).filter(_.hash == hash)
    }

  def store(entry: TTEntry): Unit =
    val index = (entry.hash & mask).toInt
    locks(index).synchronized {
      table(index) = Some(entry)
    }

  def clear(): Unit =
    for i <- 0 until size do locks(i).synchronized { table(i) = None }
