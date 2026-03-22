package de.nowchess.chess.main

import de.nowchess.chess.Main
import java.io.ByteArrayInputStream
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MainTest extends AnyFunSuite with Matchers:

  test("main exits cleanly when 'quit' is entered"):
    scala.Console.withIn(ByteArrayInputStream("quit\n".getBytes("UTF-8"))):
      Main.main(Array.empty)
