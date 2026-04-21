package de.nowchess.chess.exception

class ApiException(
    val status: Int,
    val code: String,
    message: String,
    val field: Option[String] = None,
) extends RuntimeException(message)

class GameNotFoundException(gameId: String) extends ApiException(404, "GAME_NOT_FOUND", s"Game $gameId not found")

class BadRequestException(code: String, message: String, field: Option[String] = None)
    extends ApiException(400, code, message, field)
