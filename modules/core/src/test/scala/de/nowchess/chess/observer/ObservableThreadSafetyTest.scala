package de.nowchess.chess.observer

import de.nowchess.api.board.{Board, Color}
import de.nowchess.chess.logic.GameHistory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable

class ObservableThreadSafetyTest extends AnyFunSuite with Matchers:

  private class TestObservable extends Observable:
    def testNotifyObservers(event: GameEvent): Unit =
      notifyObservers(event)

  private class CountingObserver extends Observer:
    @volatile private var eventCount = 0
    @volatile private var lastEvent: Option[GameEvent] = None

    def onGameEvent(event: GameEvent): Unit =
      eventCount += 1
      lastEvent = Some(event)

  private def createTestEvent(): GameEvent =
    BoardResetEvent(
      board = Board.initial,
      history = GameHistory.empty,
      turn = Color.White
    )

  test("Observable is thread-safe for concurrent subscribe and notify"):
    val observable = new TestObservable()
    val testEvent = createTestEvent()
    @volatile var raceConditionCaught = false

    // Thread 1: repeatedly notifies observers with long iteration
    val notifierThread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          for _ <- 1 to 500000 do
            observable.testNotifyObservers(testEvent)
        } catch {
          case _: java.util.ConcurrentModificationException =>
            raceConditionCaught = true
        }
      }
    })

    // Thread 2: rapidly subscribes/unsubscribes observers during notify
    val subscriberThread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          for _ <- 1 to 500000 do
            val obs = new CountingObserver()
            observable.subscribe(obs)
            observable.unsubscribe(obs)
        } catch {
          case _: java.util.ConcurrentModificationException =>
            raceConditionCaught = true
        }
      }
    })

    notifierThread.start()
    subscriberThread.start()
    notifierThread.join()
    subscriberThread.join()

    raceConditionCaught shouldBe false

  test("Observable is thread-safe for concurrent subscribe, unsubscribe, and notify"):
    val observable = new TestObservable()
    val testEvent = createTestEvent()
    val exceptions = mutable.ListBuffer[Exception]()
    val observers = mutable.ListBuffer[CountingObserver]()

    // Pre-subscribe some observers
    for _ <- 1 to 10 do
      val obs = new CountingObserver()
      observers += obs
      observable.subscribe(obs)

    // Thread 1: notifies observers
    val notifierThread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          for _ <- 1 to 5000 do
            observable.testNotifyObservers(testEvent)
        } catch {
          case e: Exception => exceptions += e
        }
      }
    })

    // Thread 2: subscribes new observers
    val subscriberThread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          for _ <- 1 to 5000 do
            val obs = new CountingObserver()
            observable.subscribe(obs)
        } catch {
          case e: Exception => exceptions += e
        }
      }
    })

    // Thread 3: unsubscribes observers
    val unsubscriberThread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          for i <- 1 to 5000 do
            if observers.nonEmpty then
              val obs = observers(i % observers.size)
              observable.unsubscribe(obs)
        } catch {
          case e: Exception => exceptions += e
        }
      }
    })

    notifierThread.start()
    subscriberThread.start()
    unsubscriberThread.start()
    notifierThread.join()
    subscriberThread.join()
    unsubscriberThread.join()

    exceptions.isEmpty shouldBe true

  test("Observable.observerCount is thread-safe during concurrent modifications"):
    val observable = new TestObservable()
    val exceptions = mutable.ListBuffer[Exception]()
    val countResults = mutable.ListBuffer[Int]()

    // Thread 1: subscribes observers
    val subscriberThread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          for _ <- 1 to 500 do
            observable.subscribe(new CountingObserver())
        } catch {
          case e: Exception => exceptions += e
        }
      }
    })

    // Thread 2: reads observer count
    val readerThread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          for _ <- 1 to 500 do
            val count = observable.observerCount
            countResults += count
        } catch {
          case e: Exception => exceptions += e
        }
      }
    })

    subscriberThread.start()
    readerThread.start()
    subscriberThread.join()
    readerThread.join()

    exceptions.isEmpty shouldBe true
    // Count should never go backwards
    for i <- 1 until countResults.size do
      countResults(i) >= countResults(i - 1) shouldBe true
