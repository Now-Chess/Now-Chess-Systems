package de.nowchess.analysis.error

import jakarta.ws.rs.core.{MediaType, Response}
import jakarta.ws.rs.ext.{ExceptionMapper, Provider}

@Provider
class AnalysisExceptionMapper extends ExceptionMapper[AnalysisException]:
  def toResponse(ex: AnalysisException): Response =
    Response
      .status(ex.status)
      .entity(AnalysisErrorDto(ex.code, ex.getMessage))
      .`type`(MediaType.APPLICATION_JSON)
      .build()
