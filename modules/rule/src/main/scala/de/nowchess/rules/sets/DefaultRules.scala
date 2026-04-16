package de.nowchess.rules.sets

import de.nowchess.api.board.*
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.rules.RuleSet

import scala.annotation.tailrec

/** Standard chess rules implementation. Handles move generation, validation, check/checkmate/stalemate detection.
  */
object DefaultRules extends RuleSet:

  /** Represents a position for threefold repetition (board state + turn + castling + en passant). */
  private case class Position(
      board: Board,
      turn: Color,
      castlingRights: CastlingRights,
      enPassantSquare: Option[Square],
  )

  // ── Direction vectors ──────────────────────────────────────────────
  private val RookDirs: List[(Int, Int)]   = List((1, 0), (-1, 0), (0, 1), (0, -1))
  private val BishopDirs: List[(Int, Int)] = List((1, 1), (1, -1), (-1, 1), (-1, -1))
  private val QueenDirs: List[(Int, Int)]  = RookDirs ++ BishopDirs
  private val KnightJumps: List[(Int, Int)] =
    List((2, 1), (2, -1), (-2, 1), (-2, -1), (1, 2), (1, -2), (-1, 2), (-1, -2))

  // ── Pawn configuration helpers ─────────────────────────────────────
  private def pawnForward(color: Color): Int   = if color == Color.White then 1 else -1
  private def pawnStartRank(color: Color): Int = if color == Color.White then 1 else 6
  private def pawnPromoRank(color: Color): Int = if color == Color.White then 7 else 0

  // ── Public API ─────────────────────────────────────────────────────

  override def candidateMoves(context: GameContext)(square: Square): List[Move] =
    context.board.pieceAt(square).fold(List.empty[Move]) { piece =>
      if piece.color != context.turn then List.empty[Move]
      else
        piece.pieceType match
          case PieceType.Pawn   => pawnCandidates(context, square, piece.color)
          case PieceType.Knight => knightCandidates(context, square, piece.color)
          case PieceType.Bishop => slidingMoves(context, square, piece.color, BishopDirs)
          case PieceType.Rook   => slidingMoves(context, square, piece.color, RookDirs)
          case PieceType.Queen  => slidingMoves(context, square, piece.color, QueenDirs)
          case PieceType.King   => kingCandidates(context, square, piece.color)
    }

  override def legalMoves(context: GameContext)(square: Square): List[Move] =
    candidateMoves(context)(square).filter { move =>
      !leavesKingInCheck(context, move)
    }

  override def allLegalMoves(context: GameContext): List[Move] =
    Square.all.flatMap(sq => legalMoves(context)(sq)).toList

  override def isCheck(context: GameContext): Boolean =
    kingSquare(context.board, context.turn)
      .fold(false)(sq => isAttackedBy(context.board, sq, context.turn.opposite))

  override def isCheckmate(context: GameContext): Boolean =
    isCheck(context) && allLegalMoves(context).isEmpty

  override def isStalemate(context: GameContext): Boolean =
    !isCheck(context) && allLegalMoves(context).isEmpty

  override def isInsufficientMaterial(context: GameContext): Boolean =
    insufficientMaterial(context.board)

  override def isFiftyMoveRule(context: GameContext): Boolean =
    context.halfMoveClock >= 100

  override def isThreefoldRepetition(context: GameContext): Boolean =
    val currentPosition = Position(
      board = context.board,
      turn = context.turn,
      castlingRights = context.castlingRights,
      enPassantSquare = context.enPassantSquare,
    )
    countPositionOccurrences(context, currentPosition) >= 3

  private def countPositionOccurrences(context: GameContext, targetPosition: Position): Int =
    try
      var count     = 0
      var tempCtx   = GameContext(
        board = context.initialBoard,
        turn = Color.White,
        castlingRights = CastlingRights.Initial,
        enPassantSquare = None,
        halfMoveClock = 0,
        moves = List.empty,
        initialBoard = context.initialBoard,
      )
      var tempPos   = Position(tempCtx.board, tempCtx.turn, tempCtx.castlingRights, tempCtx.enPassantSquare)
      if tempPos == targetPosition then count += 1

      for move <- context.moves do
        tempCtx = applyMove(tempCtx)(move)
        tempPos = Position(
          board = tempCtx.board,
          turn = tempCtx.turn,
          castlingRights = tempCtx.castlingRights,
          enPassantSquare = tempCtx.enPassantSquare,
        )
        if tempPos == targetPosition then count += 1

      count
    catch
      case _: Exception =>
        // If replay fails, conservatively count only the current position (never triggers a draw)
        1

  // ── Sliding pieces (Bishop, Rook, Queen) ───────────────────────────

  private def slidingMoves(
      context: GameContext,
      from: Square,
      color: Color,
      dirs: List[(Int, Int)],
  ): List[Move] =
    dirs.flatMap(dir => castRay(context.board, from, color, dir))

  private def castRay(
      board: Board,
      from: Square,
      color: Color,
      dir: (Int, Int),
  ): List[Move] =
    @tailrec
    def loop(sq: Square, acc: List[Move]): List[Move] =
      sq.offset(dir._1, dir._2) match
        case None => acc
        case Some(next) =>
          board.pieceAt(next) match
            case None                        => loop(next, Move(from, next) :: acc)
            case Some(p) if p.color != color => Move(from, next, MoveType.Normal(isCapture = true)) :: acc
            case Some(_)                     => acc
    loop(from, Nil).reverse

  // ── Knight ─────────────────────────────────────────────────────────

  private def knightCandidates(
      context: GameContext,
      from: Square,
      color: Color,
  ): List[Move] =
    KnightJumps.flatMap { (df, dr) =>
      from.offset(df, dr).flatMap { to =>
        context.board.pieceAt(to) match
          case Some(p) if p.color == color => None
          case Some(_)                     => Some(Move(from, to, MoveType.Normal(isCapture = true)))
          case None                        => Some(Move(from, to))
      }
    }

  // ── King ───────────────────────────────────────────────────────────

  private def kingCandidates(
      context: GameContext,
      from: Square,
      color: Color,
  ): List[Move] =
    val steps = QueenDirs.flatMap { (df, dr) =>
      from.offset(df, dr).flatMap { to =>
        context.board.pieceAt(to) match
          case Some(p) if p.color == color => None
          case Some(_)                     => Some(Move(from, to, MoveType.Normal(isCapture = true)))
          case None                        => Some(Move(from, to))
      }
    }
    steps ++ castlingCandidates(context, from, color)

  // ── Castling ───────────────────────────────────────────────────────

  private case class CastlingMove(
      kingFromAlg: String,
      kingToAlg: String,
      middleAlg: String,
      rookFromAlg: String,
      moveType: MoveType,
  )

  private def castlingCandidates(
      context: GameContext,
      from: Square,
      color: Color,
  ): List[Move] =
    color match
      case Color.White => whiteCastles(context, from)
      case Color.Black => blackCastles(context, from)

  private def whiteCastles(context: GameContext, from: Square): List[Move] =
    val expected = Square.fromAlgebraic("e1").getOrElse(from)
    if from != expected then List.empty
    else
      val moves = scala.collection.mutable.ListBuffer[Move]()
      addCastleMove(
        context,
        moves,
        context.castlingRights.whiteKingSide,
        CastlingMove("e1", "g1", "f1", "h1", MoveType.CastleKingside),
      )
      addCastleMove(
        context,
        moves,
        context.castlingRights.whiteQueenSide,
        CastlingMove("e1", "c1", "d1", "a1", MoveType.CastleQueenside),
      )
      moves.toList

  private def blackCastles(context: GameContext, from: Square): List[Move] =
    val expected = Square.fromAlgebraic("e8").getOrElse(from)
    if from != expected then List.empty
    else
      val moves = scala.collection.mutable.ListBuffer[Move]()
      addCastleMove(
        context,
        moves,
        context.castlingRights.blackKingSide,
        CastlingMove("e8", "g8", "f8", "h8", MoveType.CastleKingside),
      )
      addCastleMove(
        context,
        moves,
        context.castlingRights.blackQueenSide,
        CastlingMove("e8", "c8", "d8", "a8", MoveType.CastleQueenside),
      )
      moves.toList

  private def queensideBSquare(kingToAlg: String): List[String] =
    kingToAlg match
      case "c1" => List("b1")
      case "c8" => List("b8")
      case _    => List.empty

  private def addCastleMove(
      context: GameContext,
      moves: scala.collection.mutable.ListBuffer[Move],
      castlingRight: Boolean,
      castlingMove: CastlingMove,
  ): Unit =
    if castlingRight then
      val clearSqs = (List(castlingMove.middleAlg, castlingMove.kingToAlg) ++ queensideBSquare(castlingMove.kingToAlg))
        .flatMap(Square.fromAlgebraic)
      if squaresEmpty(context.board, clearSqs) then
        for
          kf <- Square.fromAlgebraic(castlingMove.kingFromAlg)
          km <- Square.fromAlgebraic(castlingMove.middleAlg)
          kt <- Square.fromAlgebraic(castlingMove.kingToAlg)
          rf <- Square.fromAlgebraic(castlingMove.rookFromAlg)
        do
          val color       = context.turn
          val kingPresent = context.board.pieceAt(kf).exists(p => p.color == color && p.pieceType == PieceType.King)
          val rookPresent = context.board.pieceAt(rf).exists(p => p.color == color && p.pieceType == PieceType.Rook)
          val squaresSafe =
            !isAttackedBy(context.board, kf, color.opposite) &&
              !isAttackedBy(context.board, km, color.opposite) &&
              !isAttackedBy(context.board, kt, color.opposite)

          if kingPresent && rookPresent && squaresSafe then moves += Move(kf, kt, castlingMove.moveType)

  private def squaresEmpty(board: Board, squares: List[Square]): Boolean =
    squares.forall(sq => board.pieceAt(sq).isEmpty)

  // ── Pawn ───────────────────────────────────────────────────────────

  private def pawnCandidates(
      context: GameContext,
      from: Square,
      color: Color,
  ): List[Move] =
    val fwd       = pawnForward(color)
    val startRank = pawnStartRank(color)
    val promoRank = pawnPromoRank(color)

    val single = from.offset(0, fwd).filter(to => context.board.pieceAt(to).isEmpty)
    val double = Option
      .when(from.rank.ordinal == startRank) {
        from.offset(0, fwd).flatMap { mid =>
          Option
            .when(context.board.pieceAt(mid).isEmpty) {
              from.offset(0, fwd * 2).filter(to => context.board.pieceAt(to).isEmpty)
            }
            .flatten
        }
      }
      .flatten

    val diagonalCaptures = List(-1, 1).flatMap { df =>
      from.offset(df, fwd).flatMap { to =>
        context.board.pieceAt(to).filter(_.color != color).map(_ => to)
      }
    }

    val epCaptures: List[Move] = context.enPassantSquare.toList.flatMap { epSq =>
      List(-1, 1).flatMap { df =>
        from.offset(df, fwd).filter(_ == epSq).map { to =>
          Move(from, epSq, MoveType.EnPassant)
        }
      }
    }

    def toMoves(dest: Square, isCapture: Boolean): List[Move] =
      if dest.rank.ordinal == promoRank then
        List(
          PromotionPiece.Queen,
          PromotionPiece.Rook,
          PromotionPiece.Bishop,
          PromotionPiece.Knight,
        ).map(pt => Move(from, dest, MoveType.Promotion(pt)))
      else List(Move(from, dest, MoveType.Normal(isCapture = isCapture)))

    val stepSquares  = single.toList ++ double.toList
    val stepMoves    = stepSquares.flatMap(dest => toMoves(dest, isCapture = false))
    val captureMoves = diagonalCaptures.flatMap(dest => toMoves(dest, isCapture = true))
    stepMoves ++ captureMoves ++ epCaptures

  // ── Check detection ────────────────────────────────────────────────

  private def kingSquare(board: Board, color: Color): Option[Square] =
    Square.all.find(sq => board.pieceAt(sq).exists(p => p.color == color && p.pieceType == PieceType.King))

  private def isAttackedBy(board: Board, target: Square, attacker: Color): Boolean =
    Square.all.exists { sq =>
      board.pieceAt(sq).fold(false) { p =>
        p.color == attacker && squareAttacks(board, sq, p, target)
      }
    }

  private def squareAttacks(board: Board, from: Square, piece: Piece, target: Square): Boolean =
    val fwd = pawnForward(piece.color)
    piece.pieceType match
      case PieceType.Pawn =>
        from.offset(-1, fwd).contains(target) || from.offset(1, fwd).contains(target)
      case PieceType.Knight =>
        KnightJumps.exists((df, dr) => from.offset(df, dr).contains(target))
      case PieceType.Bishop => rayReaches(board, from, BishopDirs, target)
      case PieceType.Rook   => rayReaches(board, from, RookDirs, target)
      case PieceType.Queen  => rayReaches(board, from, QueenDirs, target)
      case PieceType.King =>
        QueenDirs.exists((df, dr) => from.offset(df, dr).contains(target))

  private def rayReaches(board: Board, from: Square, dirs: List[(Int, Int)], target: Square): Boolean =
    dirs.exists { dir =>
      @tailrec
      def loop(sq: Square): Boolean = sq.offset(dir._1, dir._2) match
        case None                                      => false
        case Some(next) if next == target              => true
        case Some(next) if board.pieceAt(next).isEmpty => loop(next)
        case Some(_)                                   => false
      loop(from)
    }

  private def leavesKingInCheck(context: GameContext, move: Move): Boolean =
    val nextBoard   = context.board.applyMove(move)
    val nextContext = context.withBoard(nextBoard)
    isCheck(nextContext)

  // ── Move application ───────────────────────────────────────────────

  override def applyMove(context: GameContext)(move: Move): GameContext =
    val color = context.turn
    val board = context.board

    val newBoard = move.moveType match
      case MoveType.CastleKingside  => applyCastle(board, color, kingside = true)
      case MoveType.CastleQueenside => applyCastle(board, color, kingside = false)
      case MoveType.EnPassant       => applyEnPassant(board, move)
      case MoveType.Promotion(pp)   => applyPromotion(board, move, color, pp)
      case MoveType.Normal(_)       => board.applyMove(move)

    val newCastlingRights  = updateCastlingRights(context.castlingRights, board, move, color)
    val newEnPassantSquare = computeEnPassantSquare(board, move)
    val isCapture = move.moveType match
      case MoveType.Normal(capture) => capture
      case MoveType.EnPassant       => true
      case _                        => board.pieceAt(move.to).isDefined
    val isPawnMove = board.pieceAt(move.from).exists(_.pieceType == PieceType.Pawn)
    val newClock   = if isPawnMove || isCapture then 0 else context.halfMoveClock + 1

    context
      .withBoard(newBoard)
      .withTurn(color.opposite)
      .withCastlingRights(newCastlingRights)
      .withEnPassantSquare(newEnPassantSquare)
      .withHalfMoveClock(newClock)
      .withMove(move)

  private def applyCastle(board: Board, color: Color, kingside: Boolean): Board =
    val rank = if color == Color.White then Rank.R1 else Rank.R8
    val (kingFrom, kingTo, rookFrom, rookTo) =
      if kingside then (Square(File.E, rank), Square(File.G, rank), Square(File.H, rank), Square(File.F, rank))
      else (Square(File.E, rank), Square(File.C, rank), Square(File.A, rank), Square(File.D, rank))
    val king = board.pieceAt(kingFrom).getOrElse(Piece(color, PieceType.King))
    val rook = board.pieceAt(rookFrom).getOrElse(Piece(color, PieceType.Rook))
    board
      .removed(kingFrom)
      .removed(rookFrom)
      .updated(kingTo, king)
      .updated(rookTo, rook)

  private def applyEnPassant(board: Board, move: Move): Board =
    val capturedRank   = move.from.rank // the captured pawn is on the same rank as the moving pawn
    val capturedSquare = Square(move.to.file, capturedRank)
    board.applyMove(move).removed(capturedSquare)

  private def applyPromotion(board: Board, move: Move, color: Color, pp: PromotionPiece): Board =
    val promotedType = pp match
      case PromotionPiece.Queen  => PieceType.Queen
      case PromotionPiece.Rook   => PieceType.Rook
      case PromotionPiece.Bishop => PieceType.Bishop
      case PromotionPiece.Knight => PieceType.Knight
    board.removed(move.from).updated(move.to, Piece(color, promotedType))

  private def updateCastlingRights(rights: CastlingRights, board: Board, move: Move, color: Color): CastlingRights =
    val piece      = board.pieceAt(move.from)
    val isKingMove = piece.exists(_.pieceType == PieceType.King)
    val isRookMove = piece.exists(_.pieceType == PieceType.Rook)

    // Helper to check if a square is a rook's starting square
    val whiteKingsideRook  = Square(File.H, Rank.R1)
    val whiteQueensideRook = Square(File.A, Rank.R1)
    val blackKingsideRook  = Square(File.H, Rank.R8)
    val blackQueensideRook = Square(File.A, Rank.R8)

    val afterKingMove = if isKingMove then rights.revokeColor(color) else rights

    val afterRookMove =
      if !isRookMove then afterKingMove
      else
        move.from match
          case `whiteKingsideRook`  => afterKingMove.revokeKingSide(Color.White)
          case `whiteQueensideRook` => afterKingMove.revokeQueenSide(Color.White)
          case `blackKingsideRook`  => afterKingMove.revokeKingSide(Color.Black)
          case `blackQueensideRook` => afterKingMove.revokeQueenSide(Color.Black)
          case _                    => afterKingMove

    // Also revoke if a rook is captured
    move.to match
      case `whiteKingsideRook`  => afterRookMove.revokeKingSide(Color.White)
      case `whiteQueensideRook` => afterRookMove.revokeQueenSide(Color.White)
      case `blackKingsideRook`  => afterRookMove.revokeKingSide(Color.Black)
      case `blackQueensideRook` => afterRookMove.revokeQueenSide(Color.Black)
      case _                    => afterRookMove

  private def computeEnPassantSquare(board: Board, move: Move): Option[Square] =
    val piece = board.pieceAt(move.from)
    val isDoublePawnPush = piece.exists(_.pieceType == PieceType.Pawn) &&
      math.abs(move.to.rank.ordinal - move.from.rank.ordinal) == 2
    if isDoublePawnPush then
      // EP square is the square the pawn passed through
      val epRankOrd = (move.from.rank.ordinal + move.to.rank.ordinal) / 2
      Some(Square(move.from.file, Rank.values(epRankOrd)))
    else None

  // ── Insufficient material ──────────────────────────────────────────

  private def squareColor(sq: Square): Int = (sq.file.ordinal + sq.rank.ordinal) % 2

  private def insufficientMaterial(board: Board): Boolean =
    val nonKings = board.pieces.toList.filter { case (_, p) => p.pieceType != PieceType.King }
    nonKings match
      case Nil                                                                                => true
      case List((_, p)) if p.pieceType == PieceType.Bishop || p.pieceType == PieceType.Knight => true
      case bishops if bishops.forall { case (_, p) => p.pieceType == PieceType.Bishop }       =>
        // All non-king pieces are bishops: draw only if they all share the same square color
        bishops.map { case (sq, _) => squareColor(sq) }.distinct.sizeIs == 1
      case _ => false
