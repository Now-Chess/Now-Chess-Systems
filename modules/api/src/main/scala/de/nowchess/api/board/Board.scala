package de.nowchess.api.board

opaque type Board = Map[Square, Piece]

object Board:

  def apply(pieces: Map[Square, Piece]): Board = pieces

  extension (b: Board)
    def pieceAt(sq: Square): Option[Piece] = b.get(sq)
    def updated(sq: Square, piece: Piece): Board = b.updated(sq, piece)
    def removed(sq: Square): Board = b.removed(sq)
    def withMove(from: Square, to: Square): (Board, Option[Piece]) =
      val captured = b.get(to)
      val updatedBoard = b.removed(from).updated(to, b(from))
      (updatedBoard, captured)
    def applyMove(move: de.nowchess.api.move.Move): Board =
      val (updatedBoard, _) = b.withMove(move.from, move.to)
      updatedBoard
    def pieces: Map[Square, Piece] = b

  val initial: Board =
    val backRank: Vector[PieceType] = Vector(
      PieceType.Rook, PieceType.Knight, PieceType.Bishop, PieceType.Queen,
      PieceType.King, PieceType.Bishop, PieceType.Knight, PieceType.Rook
    )
    val entries = for
      fileIdx <- 0 until 8
      (color, rank, pieceType) <- Seq(
        (Color.White, Rank.R1, backRank(fileIdx)),
        (Color.White, Rank.R2, PieceType.Pawn),
        (Color.Black, Rank.R8, backRank(fileIdx)),
        (Color.Black, Rank.R7, PieceType.Pawn)
      )
    yield Square(File.values(fileIdx), rank) -> Piece(color, pieceType)
    Board(entries.toMap)
