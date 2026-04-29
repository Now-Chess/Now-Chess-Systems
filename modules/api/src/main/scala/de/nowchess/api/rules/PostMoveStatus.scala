package de.nowchess.api.rules

final case class PostMoveStatus(
    isCheckmate: Boolean,
    isStalemate: Boolean,
    isInsufficientMaterial: Boolean,
    isCheck: Boolean,
    isThreefoldRepetition: Boolean,
)
