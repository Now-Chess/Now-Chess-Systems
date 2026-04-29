package de.nowchess.account.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class ChallengeColorConverter extends AttributeConverter[ChallengeColor, String]:
  override def convertToDatabaseColumn(attribute: ChallengeColor): String =
    Option(attribute).map(_.toString).orNull

  override def convertToEntityAttribute(dbData: String): ChallengeColor =
    Option(dbData).map(ChallengeColor.valueOf).orNull
