package de.nowchess.api.response

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ApiResponseTest extends AnyFunSuite with Matchers:

  test("ApiResponse factories and payload wrappers keep values") {
    val r = ApiResponse.Success(42)
    r.data shouldBe 42

    val err = ApiError("CODE", "msg")
    ApiResponse.Failure(List(err)).errors shouldBe List(err)
    ApiResponse.error(err) shouldBe ApiResponse.Failure(List(err))

    val e = ApiError("CODE", "message")
    e.code shouldBe "CODE"
    e.message shouldBe "message"
    e.field shouldBe None
    ApiError("INVALID", "bad value", Some("email")).field shouldBe Some("email")
  }

  test("Pagination.totalPages handles normal and guarded inputs") {
    Pagination(page = 0, pageSize = 10, totalItems = 30).totalPages shouldBe 3
    Pagination(page = 0, pageSize = 10, totalItems = 25).totalPages shouldBe 3
    Pagination(page = 0, pageSize = 10, totalItems = 0).totalPages shouldBe 0
    Pagination(page = 0, pageSize = 0, totalItems = 100).totalPages shouldBe 0
    Pagination(page = 0, pageSize = -1, totalItems = 100).totalPages shouldBe 0
  }

  test("PagedResponse holds items and pagination") {
    val pagination = Pagination(page = 1, pageSize = 5, totalItems = 20)
    val pr         = PagedResponse(List("a", "b"), pagination)
    pr.items shouldBe List("a", "b")
    pr.pagination shouldBe pagination
  }
