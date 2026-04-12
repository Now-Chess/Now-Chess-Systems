package de.nowchess.api.board

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CastlingRightsTest extends AnyFunSuite with Matchers:

  test("hasAnyRights and hasRights reflect current flags"):
    val rights = CastlingRights(
      whiteKingSide = true,
      whiteQueenSide = false,
      blackKingSide = false,
      blackQueenSide = true,
    )

    rights.hasAnyRights shouldBe true
    rights.hasRights(Color.White) shouldBe true
    rights.hasRights(Color.Black) shouldBe true

    CastlingRights.None.hasAnyRights shouldBe false
    CastlingRights.None.hasRights(Color.White) shouldBe false
    CastlingRights.None.hasRights(Color.Black) shouldBe false

  test("revokeColor clears both castling sides for selected color"):
    val all = CastlingRights.All

    val whiteRevoked = all.revokeColor(Color.White)
    whiteRevoked.whiteKingSide shouldBe false
    whiteRevoked.whiteQueenSide shouldBe false
    whiteRevoked.blackKingSide shouldBe true
    whiteRevoked.blackQueenSide shouldBe true

    val blackRevoked = all.revokeColor(Color.Black)
    blackRevoked.whiteKingSide shouldBe true
    blackRevoked.whiteQueenSide shouldBe true
    blackRevoked.blackKingSide shouldBe false
    blackRevoked.blackQueenSide shouldBe false

  test("revokeKingSide and revokeQueenSide disable only requested side"):
    val all = CastlingRights.All

    val whiteKingSideRevoked = all.revokeKingSide(Color.White)
    whiteKingSideRevoked.whiteKingSide shouldBe false
    whiteKingSideRevoked.whiteQueenSide shouldBe true

    val whiteQueenSideRevoked = all.revokeQueenSide(Color.White)
    whiteQueenSideRevoked.whiteKingSide shouldBe true
    whiteQueenSideRevoked.whiteQueenSide shouldBe false

    val blackKingSideRevoked = all.revokeKingSide(Color.Black)
    blackKingSideRevoked.blackKingSide shouldBe false
    blackKingSideRevoked.blackQueenSide shouldBe true

    val blackQueenSideRevoked = all.revokeQueenSide(Color.Black)
    blackQueenSideRevoked.blackKingSide shouldBe true
    blackQueenSideRevoked.blackQueenSide shouldBe false
