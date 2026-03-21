package de.nowchess.chess

enum Color:
  case White, Black

  def opposite: Color = this match
    case White => Black
    case Black => White

  def label: String = this match
    case White => "White"
    case Black => "Black"

enum PieceType:
  case King, Queen, Rook, Bishop, Knight, Pawn

  def label: String = this match
    case King   => "King"
    case Queen  => "Queen"
    case Rook   => "Rook"
    case Bishop => "Bishop"
    case Knight => "Knight"
    case Pawn   => "Pawn"

final case class Piece(color: Color, pieceType: PieceType):
  def unicode: String = (color, pieceType) match
    case (Color.White, PieceType.King)   => "\u2654"
    case (Color.White, PieceType.Queen)  => "\u2655"
    case (Color.White, PieceType.Rook)   => "\u2656"
    case (Color.White, PieceType.Bishop) => "\u2657"
    case (Color.White, PieceType.Knight) => "\u2658"
    case (Color.White, PieceType.Pawn)   => "\u2659"
    case (Color.Black, PieceType.King)   => "\u265A"
    case (Color.Black, PieceType.Queen)  => "\u265B"
    case (Color.Black, PieceType.Rook)   => "\u265C"
    case (Color.Black, PieceType.Bishop) => "\u265D"
    case (Color.Black, PieceType.Knight) => "\u265E"
    case (Color.Black, PieceType.Pawn)   => "\u265F"

/** Zero-based file (0=a..7=h) and rank (0=rank1..7=rank8). */
final case class Square(file: Int, rank: Int):
  require(file >= 0 && file <= 7 && rank >= 0 && rank <= 7, s"Square out of bounds: $file,$rank")

  def label: String = s"${('a' + file).toChar}${rank + 1}"

opaque type Board = Map[Square, Piece]

object Board:
  def apply(pieces: Map[Square, Piece]): Board = pieces

  extension (b: Board)
    def pieceAt(sq: Square): Option[Piece]                    = b.get(sq)
    def withMove(from: Square, to: Square): (Board, Option[Piece]) =
      val captured = b.get(to)
      val updated  = b.removed(from).updated(to, b(from))
      (updated, captured)
    def pieces: Map[Square, Piece] = b

  val initial: Board =
    val backRank: Vector[PieceType] =
      Vector(
        PieceType.Rook,
        PieceType.Knight,
        PieceType.Bishop,
        PieceType.Queen,
        PieceType.King,
        PieceType.Bishop,
        PieceType.Knight,
        PieceType.Rook
      )

    val entries = for
      file <- 0 until 8
      (color, rank, row) <- Seq(
        (Color.White, 0, backRank(file)),
        (Color.White, 1, PieceType.Pawn),
        (Color.Black, 7, backRank(file)),
        (Color.Black, 6, PieceType.Pawn)
      )
    yield Square(file, rank) -> Piece(color, row)

    Board(entries.toMap)
