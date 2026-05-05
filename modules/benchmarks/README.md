# JMH Benchmarks

Java Microbenchmark Harness (JMH) benchmarks for performance-critical components.

## Building

```bash
./gradlew modules:benchmarks:jmhJar
```

Produces `modules/benchmarks/build/libs/benchmarks-1.0-SNAPSHOT-jmh.jar`.

## Running Benchmarks

Since these benchmarks are written in Scala, JMH auto-discovery via jar packaging doesn't work. Instead, run benchmarks via classpath:

```bash
./gradlew modules:benchmarks:run --args='de.nowchess.benchmarks.MoveBenchmark'
```

Or compile and run with explicit classpath:
```bash
./gradlew modules:benchmarks:compileScala
java -cp "modules/benchmarks/build/classes/scala/main:modules/benchmarks/build/resources/main:$(./gradlew -q printClasspath)" \
  org.openjdk.jmh.Main de.nowchess.benchmarks.MoveBenchmark
```

Adjust JMH options:
- `-f 1` — 1 fork (faster iteration, less reliable)
- `-i 3 -w 2` — 3 measurement iterations, 2 warmup iterations
- `-bm avgt` — average time mode
- `-tu us` — time unit (microseconds)

Example:
```bash
java -cp "..." org.openjdk.jmh.Main de.nowchess.benchmarks.MoveBenchmark -f 1 -i 3 -w 2 -bm avgt
```

## Writing Benchmarks

Create Scala class with `@Benchmark` methods:

```scala
@BenchmarkMode(Array(Mode.AverageTime, Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
class SomeBenchmark {
  @Setup(Level.Trial)
  def setup(): Unit = ???

  @org.openjdk.jmh.annotations.Benchmark
  def benchmarkSomething(bh: Blackhole): Unit = {
    val result = expensiveOperation()
    bh.consume(result)  // Prevent dead-code elimination
  }
}
```

## Benchmark Modes

- `AverageTime` — average execution time per operation
- `Throughput` — operations per unit time
- `SampleTime` — latency samples (min, max, percentiles)
- `SingleShotTime` — total time to execute N ops once
- `All` — run all modes

## Limitations

JMH's Gradle plugin doesn't fully support Scala annotation processing, so benchmarks must be run explicitly by class name rather than auto-discovered. This is a known limitation of JMH + Scala; consider Java-based benchmarks for full auto-discovery support.

## References

- [OpenJDK JMH](https://github.com/openjdk/jmh)
- [JMH Samples](https://hg.openjdk.org/code-tools/jmh/file/tip/jmh-samples)
