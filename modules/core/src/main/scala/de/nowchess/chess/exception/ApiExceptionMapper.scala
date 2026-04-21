package de.nowchess.chess.exception

import de.nowchess.api.dto.ApiErrorDto
import jakarta.ws.rs.core.{MediaType, Response}
import jakarta.ws.rs.ext.{ExceptionMapper, Provider}

@Provider
class ApiExceptionMapper extends ExceptionMapper[ApiException]:
  def toResponse(ex: ApiException): Response =
    Response
      .status(ex.status)
      .entity(ApiErrorDto(ex.code, ex.getMessage, ex.field))
      .`type`(MediaType.APPLICATION_JSON)
      .build()
