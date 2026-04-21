package de.nowchess.api.dto

final case class ImportFenRequestDto(
    fen: String,
    white: Option[PlayerInfoDto],
    black: Option[PlayerInfoDto],
)
