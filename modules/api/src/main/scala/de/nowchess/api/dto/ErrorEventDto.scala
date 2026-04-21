package de.nowchess.api.dto

final case class ErrorEventDto(`type`: String, error: ApiErrorDto)

object ErrorEventDto:
  def apply(error: ApiErrorDto): ErrorEventDto = ErrorEventDto("error", error)
