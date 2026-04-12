package de.nowchess.api.response

/** A standardised envelope for every API response.
  *
  * Success and failure are modelled as subtypes so that callers can pattern-match exhaustively.
  *
  * @tparam A
  *   the payload type for a successful response
  */
sealed trait ApiResponse[+A]

object ApiResponse:
  /** A successful response carrying a payload. */
  final case class Success[A](data: A) extends ApiResponse[A]

  /** A failed response carrying one or more errors. */
  final case class Failure(errors: List[ApiError]) extends ApiResponse[Nothing]

  /** Convenience constructor for a single-error failure. */
  def error(err: ApiError): Failure = Failure(List(err))

/** A structured error descriptor.
  *
  * @param code
  *   machine-readable error code (e.g. "INVALID_MOVE", "NOT_FOUND")
  * @param message
  *   human-readable explanation
  * @param field
  *   optional field name when the error relates to a specific input
  */
final case class ApiError(
    code: String,
    message: String,
    field: Option[String] = None,
)

/** Pagination metadata for list responses.
  *
  * @param page
  *   current 0-based page index
  * @param pageSize
  *   number of items per page
  * @param totalItems
  *   total number of items across all pages
  */
final case class Pagination(
    page: Int,
    pageSize: Int,
    totalItems: Long,
):
  def totalPages: Int =
    if pageSize <= 0 then 0
    else Math.ceil(totalItems.toDouble / pageSize).toInt

/** A paginated list response envelope.
  *
  * @param items
  *   the items on the current page
  * @param pagination
  *   pagination metadata
  * @tparam A
  *   the item type
  */
final case class PagedResponse[A](
    items: List[A],
    pagination: Pagination,
)
