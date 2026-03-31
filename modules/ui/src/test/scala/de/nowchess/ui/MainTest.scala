package de.nowchess.ui

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class MainTest extends AnyFunSuite with Matchers {

  test("main should execute and quit immediately when fed 'quit'") {
    val in = new ByteArrayInputStream("quit\n".getBytes)
    val out = new ByteArrayOutputStream()

    Console.withIn(in) {
      Console.withOut(out) {
        Main.main(Array.empty)
      }
    }
    
    val output = out.toString
    output should include ("Game over. Goodbye!")
  }
}
