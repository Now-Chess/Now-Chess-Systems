package de.nowchess.api.dto

final case class ApiErrorDto(code: String, message: String, field: Option[String])
