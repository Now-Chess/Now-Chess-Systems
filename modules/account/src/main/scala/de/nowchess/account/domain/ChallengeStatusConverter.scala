package de.nowchess.account.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class ChallengeStatusConverter extends AttributeConverter[ChallengeStatus, String]:
  override def convertToDatabaseColumn(attribute: ChallengeStatus): String =
    Option(attribute).map(_.toString).orNull

  override def convertToEntityAttribute(dbData: String): ChallengeStatus =
    Option(dbData).map(ChallengeStatus.valueOf).orNull
