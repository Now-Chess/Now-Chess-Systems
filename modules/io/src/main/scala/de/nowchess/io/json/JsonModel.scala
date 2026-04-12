package de.nowchess.io.json

case class JsonMetadata(
    event: Option[String] = None,
    players: Option[Map[String, String]] = None,
    date: Option[String] = None,
    result: Option[String] = None,
)

case class JsonPiece(
    square: Option[String] = None,
    color: Option[String] = None,
    piece: Option[String] = None,
)

case class JsonCastlingRights(
    whiteKingSide: Option[Boolean] = None,
    whiteQueenSide: Option[Boolean] = None,
    blackKingSide: Option[Boolean] = None,
    blackQueenSide: Option[Boolean] = None,
)

case class JsonGameState(
    board: Option[List[JsonPiece]] = None,
    turn: Option[String] = None,
    castlingRights: Option[JsonCastlingRights] = None,
    enPassantSquare: Option[String] = None,
    halfMoveClock: Option[Int] = None,
)

case class JsonCapturedPieces(
    byWhite: Option[List[String]] = None,
    byBlack: Option[List[String]] = None,
)

case class JsonMoveType(
    `type`: Option[String] = None,
    isCapture: Option[Boolean] = None,
    promotionPiece: Option[String] = None,
)

case class JsonMove(
    from: Option[String] = None,
    to: Option[String] = None,
    `type`: Option[JsonMoveType] = None,
)

case class JsonGameRecord(
    metadata: Option[JsonMetadata] = None,
    gameState: Option[JsonGameState] = None,
    moveHistory: Option[String] = None,
    moves: Option[List[JsonMove]] = None,
    capturedPieces: Option[JsonCapturedPieces] = None,
    timestamp: Option[String] = None,
)
