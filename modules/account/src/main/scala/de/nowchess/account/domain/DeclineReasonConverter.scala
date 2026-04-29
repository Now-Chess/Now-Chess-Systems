package de.nowchess.account.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class DeclineReasonConverter extends AttributeConverter[DeclineReason, String]:
  override def convertToDatabaseColumn(attribute: DeclineReason): String =
    Option(attribute).map(_.toString).orNull

  override def convertToEntityAttribute(dbData: String): DeclineReason =
    Option(dbData).map(DeclineReason.valueOf).orNull
