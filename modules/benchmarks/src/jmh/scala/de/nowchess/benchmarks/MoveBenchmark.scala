package de.nowchess.benchmarks

import org.openjdk.jmh.annotations.{BenchmarkMode, Fork, Measurement, OutputTimeUnit, Setup, State, Warmup}
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.infra.Blackhole
import de.nowchess.api.board.{File, Rank, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.rules.sets.DefaultRules

import java.util.concurrent.TimeUnit

// scalafix:off DisableSyntax.var
@BenchmarkMode(Array(Mode.AverageTime, Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, warmups = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
class MoveBenchmark {
  private var gameContext: GameContext = scala.compiletime.uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    gameContext = GameContext.initial

  @org.openjdk.jmh.annotations.Benchmark
  def benchmarkAllLegalMoves(bh: Blackhole): Unit = {
    val moves = DefaultRules.allLegalMoves(gameContext)
    bh.consume(moves)
  }

  @org.openjdk.jmh.annotations.Benchmark
  def benchmarkIsCheck(bh: Blackhole): Unit = {
    val inCheck = DefaultRules.isCheck(gameContext)
    bh.consume(inCheck)
  }

  @org.openjdk.jmh.annotations.Benchmark
  def benchmarkLegalMovesFirstSquare(bh: Blackhole): Unit = {
    val firstSquare = Square(File.A, Rank.R2)
    val moves       = DefaultRules.legalMoves(gameContext)(firstSquare)
    bh.consume(moves)
  }

  @org.openjdk.jmh.annotations.Benchmark
  def benchmarkIsCheckmate(bh: Blackhole): Unit = {
    val result = DefaultRules.isCheckmate(gameContext)
    bh.consume(result)
  }
}
