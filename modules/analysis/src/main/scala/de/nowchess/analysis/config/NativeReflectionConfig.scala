package de.nowchess.analysis.config

import de.nowchess.analysis.client.{ChessApiRequestDto, ChessApiResponseDto}
import de.nowchess.analysis.dto.{AnalysisRequestDto, AnalysisResponseDto}
import de.nowchess.analysis.error.AnalysisErrorDto
import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection(
  targets = Array(
    classOf[AnalysisRequestDto],
    classOf[AnalysisResponseDto],
    classOf[ChessApiRequestDto],
    classOf[ChessApiResponseDto],
    classOf[AnalysisErrorDto],
  ),
  registerFullHierarchy = true,
)
class NativeReflectionConfig
