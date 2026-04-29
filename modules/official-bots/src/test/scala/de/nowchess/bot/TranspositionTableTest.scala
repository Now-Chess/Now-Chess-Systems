package de.nowchess.bot

import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.api.move.{Move, MoveType}
import de.nowchess.bot.logic.{TTEntry, TTFlag, TranspositionTable}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class TranspositionTableTest extends AnyFunSuite with Matchers:

  test("probe on empty table returns None"):
    val tt = TranspositionTable(sizePow2 = 4)
    tt.probe(12345L) should be(None)

  test("store then probe returns the stored entry"):
    val tt    = TranspositionTable(sizePow2 = 4)
    val move  = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal())
    val entry = TTEntry(hash = 12345L, depth = 3, score = 50, flag = TTFlag.Exact, bestMove = Some(move))
    tt.store(entry)
    val retrieved = tt.probe(12345L)
    retrieved should be(Some(entry))

  test("probe returns None when hash differs (collision guard)"):
    val tt    = TranspositionTable(sizePow2 = 4)
    val move  = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal())
    val entry = TTEntry(hash = 12345L, depth = 3, score = 50, flag = TTFlag.Exact, bestMove = Some(move))
    tt.store(entry)
    tt.probe(54321L) should be(None)

  test("clear removes all entries"):
    val tt    = TranspositionTable(sizePow2 = 4)
    val move  = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal())
    val entry = TTEntry(hash = 12345L, depth = 3, score = 50, flag = TTFlag.Exact, bestMove = Some(move))
    tt.store(entry)
    tt.probe(12345L) should be(Some(entry))
    tt.clear()
    tt.probe(12345L) should be(None)

  test("all TTFlag values store and retrieve correctly"):
    val tt   = TranspositionTable(sizePow2 = 4)
    val move = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal())

    TTFlag.values.foreach { flag =>
      val entry = TTEntry(hash = 12345L + flag.ordinal, depth = 2, score = 100, flag = flag, bestMove = Some(move))
      tt.store(entry)
      val retrieved = tt.probe(12345L + flag.ordinal)
      retrieved.map(_.flag) should be(Some(flag))
    }

  test("bestMove = None roundtrips"):
    val tt    = TranspositionTable(sizePow2 = 4)
    val entry = TTEntry(hash = 99999L, depth = 1, score = 0, flag = TTFlag.Upper, bestMove = None)
    tt.store(entry)
    val retrieved = tt.probe(99999L)
    retrieved.map(_.bestMove) should be(Some(None))

  test("always-replace overwrites at same slot"):
    val tt     = TranspositionTable(sizePow2 = 4)
    val move1  = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R4), MoveType.Normal())
    val move2  = Move(Square(File.E, Rank.R2), Square(File.E, Rank.R3), MoveType.Normal())
    val entry1 = TTEntry(hash = 12345L, depth = 2, score = 50, flag = TTFlag.Exact, bestMove = Some(move1))
    val entry2 = TTEntry(hash = 12345L, depth = 3, score = 100, flag = TTFlag.Lower, bestMove = Some(move2))

    tt.store(entry1)
    tt.probe(12345L).map(_.score) should be(Some(50))
    tt.store(entry2)
    tt.probe(12345L).map(_.score) should be(Some(100))

  test("size is 1 << sizePow2"):
    val tt = TranspositionTable(sizePow2 = 4)
    (1 << 4) should equal(16)
    val tt2 = TranspositionTable(sizePow2 = 10)
    (1 << 10) should equal(1024)
