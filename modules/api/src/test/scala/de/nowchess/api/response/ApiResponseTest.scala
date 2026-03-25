package de.nowchess.api.response

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ApiResponseTest extends AnyFunSuite with Matchers:

  test("ApiResponse.Success carries data") {
    val r = ApiResponse.Success(42)
    r.data shouldBe 42
  }

  test("ApiResponse.Failure carries error list") {
    val err = ApiError("CODE", "msg")
    val r   = ApiResponse.Failure(List(err))
    r.errors shouldBe List(err)
  }

  test("ApiResponse.error creates single-error Failure") {
    val err = ApiError("NOT_FOUND", "not found")
    val f   = ApiResponse.error(err)
    f shouldBe ApiResponse.Failure(List(err))
  }

  test("ApiError holds code and message") {
    val e = ApiError("CODE", "message")
    e.code    shouldBe "CODE"
    e.message shouldBe "message"
    e.field   shouldBe None
  }

  test("ApiError holds optional field") {
    val e = ApiError("INVALID", "bad value", Some("email"))
    e.field shouldBe Some("email")
  }

  test("Pagination.totalPages with exact division") {
    Pagination(page = 0, pageSize = 10, totalItems = 30).totalPages shouldBe 3
  }

  test("Pagination.totalPages rounds up") {
    Pagination(page = 0, pageSize = 10, totalItems = 25).totalPages shouldBe 3
  }

  test("Pagination.totalPages is 0 when totalItems is 0") {
    Pagination(page = 0, pageSize = 10, totalItems = 0).totalPages shouldBe 0
  }

  test("Pagination.totalPages is 0 when pageSize is 0") {
    Pagination(page = 0, pageSize = 0, totalItems = 100).totalPages shouldBe 0
  }

  test("Pagination.totalPages is 0 when pageSize is negative") {
    Pagination(page = 0, pageSize = -1, totalItems = 100).totalPages shouldBe 0
  }

  test("PagedResponse holds items and pagination") {
    val pagination = Pagination(page = 1, pageSize = 5, totalItems = 20)
    val pr         = PagedResponse(List("a", "b"), pagination)
    pr.items      shouldBe List("a", "b")
    pr.pagination shouldBe pagination
  }
