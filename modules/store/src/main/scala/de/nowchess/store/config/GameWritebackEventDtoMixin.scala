package de.nowchess.store.config

import com.fasterxml.jackson.databind.annotation.JsonDeserialize

abstract class GameWritebackEventDtoMixin:
  @JsonDeserialize(contentAs = classOf[java.lang.Long]) val whiteRemainingMs: Option[Long]
  @JsonDeserialize(contentAs = classOf[java.lang.Long]) val blackRemainingMs: Option[Long]
  @JsonDeserialize(contentAs = classOf[java.lang.Long]) val incrementMs: Option[Long]
  @JsonDeserialize(contentAs = classOf[java.lang.Long]) val clockLastTickAt: Option[Long]
  @JsonDeserialize(contentAs = classOf[java.lang.Long]) val clockMoveDeadline: Option[Long]
