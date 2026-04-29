package de.nowchess.rules.sets

import de.nowchess.api.board.*
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.api.rules.{PostMoveStatus, RuleSet}

/** Standard chess rules — optimized hot path.
  *
  * Internal representation: Array[Int](64), indexed by file + rank*8. Piece encoding: 0=empty, +1..+6=white
  * (P/N/B/R/Q/K), -1..-6=black. Move generation uses pre-computed ray/jump tables and an integer-encoded move word to
  * avoid heap allocation in tight loops. Check detection uses make/unmake on the mutable array instead of copying the
  * immutable Board map.
  */
// scalafix:off DisableSyntax.var
// scalafix:off DisableSyntax.return
object DefaultRules extends RuleSet:

  // ─── Piece constants ──────────────────────────────────────────────────────
  private val PAWN = 1; private val KNIGHT = 2; private val BISHOP = 3
  private val ROOK = 4; private val QUEEN  = 5; private val KING   = 6

  private inline def idx(f: Int, r: Int): Int      = f + (r << 3)
  private inline def fileOf(sq: Int): Int          = sq & 7
  private inline def rankOf(sq: Int): Int          = sq >> 3
  private inline def isEmpty(p: Int): Boolean      = p == 0
  private inline def isWhitePiece(p: Int): Boolean = p > 0
  private inline def pieceType(p: Int): Int        = if p > 0 then p else -p

  private def encodePiece(c: Color, pt: PieceType): Int =
    val raw = pt match
      case PieceType.Pawn   => PAWN; case PieceType.Knight => KNIGHT
      case PieceType.Bishop => BISHOP; case PieceType.Rook => ROOK
      case PieceType.Queen  => QUEEN; case PieceType.King  => KING
    if c == Color.White then raw else -raw

  // ─── Pre-computed tables ──────────────────────────────────────────────────

  private val KNIGHT_TARGETS: Array[Array[Int]] = Array.tabulate(64) { sq =>
    val (f, r) = (fileOf(sq), rankOf(sq))
    Array((2, 1), (2, -1), (-2, 1), (-2, -1), (1, 2), (1, -2), (-1, 2), (-1, -2)).collect {
      case (df, dr) if f + df >= 0 && f + df < 8 && r + dr >= 0 && r + dr < 8 => idx(f + df, r + dr)
    }
  }

  private val KING_TARGETS: Array[Array[Int]] = Array.tabulate(64) { sq =>
    val (f, r) = (fileOf(sq), rankOf(sq))
    Array((-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)).collect {
      case (df, dr) if f + df >= 0 && f + df < 8 && r + dr >= 0 && r + dr < 8 => idx(f + df, r + dr)
    }
  }

  // Directions 0-3: rook (N,S,E,W); 4-7: bishop (NE,NW,SE,SW)
  private val DIR_VECS: Array[(Int, Int)] =
    Array((0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (-1, 1), (1, -1), (-1, -1))

  // RAY_TABLES(sq)(d) = squares along direction d from sq, nearest first
  private val RAY_TABLES: Array[Array[Array[Int]]] = Array.tabulate(64, 8) { (sq, d) =>
    val (df, dr) = DIR_VECS(d)
    val (f, r)   = (fileOf(sq), rankOf(sq))
    val buf      = new scala.collection.mutable.ArrayBuffer[Int](7)
    var nf       = f + df; var nr = r + dr
    while nf >= 0 && nf < 8 && nr >= 0 && nr < 8 do
      buf += idx(nf, nr); nf += df; nr += dr
    buf.toArray
  }

  // PAWN_ATTACK_SOURCES(colorIdx)(target) = squares from which a pawn of that color attacks target.
  // White pawn (fwd=+1) at (f±1, r-1) attacks (f, r) → sources are at rank r-1.
  // Black pawn (fwd=-1) at (f±1, r+1) attacks (f, r) → sources are at rank r+1.
  private val PAWN_ATTACK_SOURCES: Array[Array[Array[Int]]] = Array.tabulate(2) { colorIdx =>
    val fwd = if colorIdx == 0 then 1 else -1
    Array.tabulate(64) { sq =>
      val (f, r) = (fileOf(sq), rankOf(sq))
      Array(-1, 1).collect {
        case df if f + df >= 0 && f + df < 8 && r - fwd >= 0 && r - fwd < 8 => idx(f + df, r - fwd)
      }
    }
  }

  // Pre-computed castling square indices (no runtime string parsing)
  private val A1 = idx(0, 0); private val B1 = idx(1, 0); private val C1 = idx(2, 0)
  private val D1 = idx(3, 0); private val E1 = idx(4, 0); private val F1 = idx(5, 0)
  private val G1 = idx(6, 0); private val H1 = idx(7, 0)
  private val A8 = idx(0, 7); private val B8 = idx(1, 7); private val C8 = idx(2, 7)
  private val D8 = idx(3, 7); private val E8 = idx(4, 7); private val F8 = idx(5, 7)
  private val G8 = idx(6, 7); private val H8 = idx(7, 7)

  // Thread-local mutable board and move buffer — zero heap allocation in hot loops
  private val tlBoard = ThreadLocal.withInitial[Array[Int]](() => new Array[Int](64))
  // 320 slots: theoretical max ~218 chess moves, promotion bursts add 4 per pawn-on-7th
  private val tlMoves = ThreadLocal.withInitial[Array[Int]](() => new Array[Int](320))

  // ─── Move word encoding ───────────────────────────────────────────────────
  // bits 0-5: from square, bits 6-11: to square, bits 12-15: move kind
  private val KIND_QUIET   = 0; private val KIND_CAPTURE = 1; private val KIND_EP = 2
  private val KIND_CASTLEK = 3; private val KIND_CASTLEQ = 4
  private val KIND_PROMO_Q = 5; private val KIND_PROMO_R = 6
  private val KIND_PROMO_B = 7; private val KIND_PROMO_N = 8

  private inline def encMove(from: Int, to: Int, kind: Int): Int = from | (to << 6) | (kind << 12)
  private inline def moveFrom(m: Int): Int                       = m & 63
  private inline def moveTo(m: Int): Int                         = (m >> 6) & 63
  private inline def moveKind(m: Int): Int                       = m >> 12

  // ─── Board ↔ Array[Int] ──────────────────────────────────────────────────

  private def fillBoard(board: Board, arr: Array[Int]): Unit =
    java.util.Arrays.fill(arr, 0)
    board.pieces.foreach { (sq, piece) =>
      arr(idx(sq.file.ordinal, sq.rank.ordinal)) = encodePiece(piece.color, piece.pieceType)
    }

  private def toSquare(sq: Int): Square =
    Square(File.values(fileOf(sq)), Rank.values(rankOf(sq)))

  // ─── Attack detection (reverse lookup from target) ────────────────────────
  // Cast rays/jumps FROM the target to find attackers — O(directions × ray_length) vs O(64 × ray_length)

  private def isAttackedByColor(arr: Array[Int], target: Int, byWhite: Boolean): Boolean =
    val sign     = if byWhite then 1 else -1
    val colorIdx = if byWhite then 0 else 1

    // Pawn
    val pawnSrcs = PAWN_ATTACK_SOURCES(colorIdx)(target); var i = 0
    while i < pawnSrcs.length do
      if arr(pawnSrcs(i)) == sign * PAWN then return true
      i += 1

    // Knight
    val knightSrcs = KNIGHT_TARGETS(target); i = 0
    while i < knightSrcs.length do
      if arr(knightSrcs(i)) == sign * KNIGHT then return true
      i += 1

    // King
    val kingSrcs = KING_TARGETS(target); i = 0
    while i < kingSrcs.length do
      if arr(kingSrcs(i)) == sign * KING then return true
      i += 1

    // Rook/Queen on rook rays (directions 0-3)
    val rays = RAY_TABLES(target); i = 0
    while i < 4 do
      val ray = rays(i); var j = 0
      while j < ray.length do
        val p = arr(ray(j))
        if p != 0 then
          if p == sign * ROOK || p == sign * QUEEN then return true
          j = ray.length // blocked
        j += 1
      i += 1

    // Bishop/Queen on bishop rays (directions 4-7)
    i = 4
    while i < 8 do
      val ray = rays(i); var j = 0
      while j < ray.length do
        val p = arr(ray(j))
        if p != 0 then
          if p == sign * BISHOP || p == sign * QUEEN then return true
          j = ray.length // blocked
        j += 1
      i += 1

    false

  private def findKing(arr: Array[Int], whiteKing: Boolean): Int =
    val king = if whiteKing then KING else -KING; var sq = 0
    while sq < 64 do
      if arr(sq) == king then return sq
      sq += 1
    -1

  // ─── Make/unmake for check validation ────────────────────────────────────
  // Applies move on mutable arr, tests check, undoes — no Map copy.

  private def leavesKingInCheck(arr: Array[Int], move: Int, whiteMoved: Boolean): Boolean =
    val from      = moveFrom(move); val to = moveTo(move); val kind = moveKind(move)
    val savedFrom = arr(from); val savedTo = arr(to)
    var epSq      = -1
    var rookFrom  = -1; var savedRookPiece = 0; var rookTo          = -1

    kind match
      case KIND_EP =>
        epSq = idx(fileOf(to), rankOf(from))
        arr(to) = savedFrom; arr(from) = 0; arr(epSq) = 0

      case KIND_CASTLEK =>
        rookFrom = if whiteMoved then H1 else H8
        rookTo = if whiteMoved then F1 else F8
        savedRookPiece = arr(rookFrom)
        arr(to) = savedFrom; arr(from) = 0; arr(rookTo) = savedRookPiece; arr(rookFrom) = 0

      case KIND_CASTLEQ =>
        rookFrom = if whiteMoved then A1 else A8
        rookTo = if whiteMoved then D1 else D8
        savedRookPiece = arr(rookFrom)
        arr(to) = savedFrom; arr(from) = 0; arr(rookTo) = savedRookPiece; arr(rookFrom) = 0

      case k if k >= KIND_PROMO_Q =>
        val promoted = k match
          case KIND_PROMO_Q => if whiteMoved then QUEEN else -QUEEN
          case KIND_PROMO_R => if whiteMoved then ROOK else -ROOK
          case KIND_PROMO_B => if whiteMoved then BISHOP else -BISHOP
          case _            => if whiteMoved then KNIGHT else -KNIGHT
        arr(to) = promoted; arr(from) = 0

      case _ =>
        arr(to) = savedFrom; arr(from) = 0

    val kingSq  = findKing(arr, whiteMoved)
    val inCheck = kingSq >= 0 && isAttackedByColor(arr, kingSq, !whiteMoved)

    // Undo
    arr(from) = savedFrom; arr(to) = savedTo
    if epSq >= 0 then arr(epSq) = if whiteMoved then -PAWN else PAWN
    if rookFrom >= 0 then
      arr(rookFrom) = savedRookPiece
      arr(rookTo) = 0

    inCheck

  // ─── Move generation ─────────────────────────────────────────────────────

  private def generateAll(arr: Array[Int], isWhite: Boolean, ctx: GameContext, buf: Array[Int]): Int =
    var n = 0; var sq = 0
    while sq < 64 do
      val p = arr(sq)
      if !isEmpty(p) && isWhitePiece(p) == isWhite then n = generatePiece(arr, sq, pieceType(p), isWhite, ctx, buf, n)
      sq += 1
    n

  private def generatePiece(
      arr: Array[Int],
      sq: Int,
      pt: Int,
      isWhite: Boolean,
      ctx: GameContext,
      buf: Array[Int],
      n: Int,
  ): Int =
    if pt == PAWN then generatePawnMoves(arr, sq, isWhite, ctx, buf, n)
    else if pt == KNIGHT then generateJumps(arr, sq, isWhite, KNIGHT_TARGETS(sq), buf, n)
    else if pt == BISHOP then generateRays(arr, sq, isWhite, buf, n, rookRays = false)
    else if pt == ROOK then generateRays(arr, sq, isWhite, buf, n, rookRays = true)
    else if pt == QUEEN then
      val n2 = generateRays(arr, sq, isWhite, buf, n, rookRays = true)
      generateRays(arr, sq, isWhite, buf, n2, rookRays = false)
    else generateKingMoves(arr, sq, isWhite, ctx, buf, n)

  private def generateJumps(
      arr: Array[Int],
      from: Int,
      isWhite: Boolean,
      targets: Array[Int],
      buf: Array[Int],
      start: Int,
  ): Int =
    var n = start; var i = 0
    while i < targets.length do
      val to = targets(i); val tgt = arr(to)
      if isEmpty(tgt) then
        buf(n) = encMove(from, to, KIND_QUIET); n += 1
      else if isWhitePiece(tgt) != isWhite then
        buf(n) = encMove(from, to, KIND_CAPTURE); n += 1
      i += 1
    n

  private def generateRays(
      arr: Array[Int],
      from: Int,
      isWhite: Boolean,
      buf: Array[Int],
      start: Int,
      rookRays: Boolean,
  ): Int =
    var n    = start
    val rays = RAY_TABLES(from)
    val d0   = if rookRays then 0 else 4
    val d1   = if rookRays then 4 else 8
    var d    = d0
    while d < d1 do
      val ray = rays(d); var j = 0
      while j < ray.length do
        val to = ray(j); val tgt = arr(to)
        if isEmpty(tgt) then
          buf(n) = encMove(from, to, KIND_QUIET); n += 1
        else
          if isWhitePiece(tgt) != isWhite then
            buf(n) = encMove(from, to, KIND_CAPTURE); n += 1
          j = ray.length
        j += 1
      d += 1
    n

  private def generateKingMoves(
      arr: Array[Int],
      from: Int,
      isWhite: Boolean,
      ctx: GameContext,
      buf: Array[Int],
      start: Int,
  ): Int =
    val n = generateJumps(arr, from, isWhite, KING_TARGETS(from), buf, start)
    generateCastlingMoves(arr, from, isWhite, ctx, buf, n)

  private def generateCastlingMoves(
      arr: Array[Int],
      from: Int,
      isWhite: Boolean,
      ctx: GameContext,
      buf: Array[Int],
      start: Int,
  ): Int =
    var n  = start
    val cr = ctx.castlingRights
    if isWhite && from == E1 then
      if cr.whiteKingSide && isEmpty(arr(F1)) && isEmpty(arr(G1)) &&
        arr(E1) == KING && arr(H1) == ROOK &&
        !isAttackedByColor(arr, E1, false) &&
        !isAttackedByColor(arr, F1, false) &&
        !isAttackedByColor(arr, G1, false)
      then
        buf(n) = encMove(E1, G1, KIND_CASTLEK); n += 1
      if cr.whiteQueenSide && isEmpty(arr(D1)) && isEmpty(arr(C1)) && isEmpty(arr(B1)) &&
        arr(E1) == KING && arr(A1) == ROOK &&
        !isAttackedByColor(arr, E1, false) &&
        !isAttackedByColor(arr, D1, false) &&
        !isAttackedByColor(arr, C1, false)
      then
        buf(n) = encMove(E1, C1, KIND_CASTLEQ); n += 1
    else if !isWhite && from == E8 then
      if cr.blackKingSide && isEmpty(arr(F8)) && isEmpty(arr(G8)) &&
        arr(E8) == -KING && arr(H8) == -ROOK &&
        !isAttackedByColor(arr, E8, true) &&
        !isAttackedByColor(arr, F8, true) &&
        !isAttackedByColor(arr, G8, true)
      then
        buf(n) = encMove(E8, G8, KIND_CASTLEK); n += 1
      if cr.blackQueenSide && isEmpty(arr(D8)) && isEmpty(arr(C8)) && isEmpty(arr(B8)) &&
        arr(E8) == -KING && arr(A8) == -ROOK &&
        !isAttackedByColor(arr, E8, true) &&
        !isAttackedByColor(arr, D8, true) &&
        !isAttackedByColor(arr, C8, true)
      then
        buf(n) = encMove(E8, C8, KIND_CASTLEQ); n += 1
    n

  private def generatePawnMoves(
      arr: Array[Int],
      from: Int,
      isWhite: Boolean,
      ctx: GameContext,
      buf: Array[Int],
      start: Int,
  ): Int =
    var n         = start
    val f         = fileOf(from); val r = rankOf(from)
    val fwd       = if isWhite then 1 else -1
    val startRank = if isWhite then 1 else 6
    val promoRank = if isWhite then 7 else 0
    val r1        = r + fwd

    if r1 >= 0 && r1 < 8 then
      val to1 = idx(f, r1)
      if isEmpty(arr(to1)) then
        if r1 == promoRank then
          buf(n) = encMove(from, to1, KIND_PROMO_Q); n += 1
          buf(n) = encMove(from, to1, KIND_PROMO_R); n += 1
          buf(n) = encMove(from, to1, KIND_PROMO_B); n += 1
          buf(n) = encMove(from, to1, KIND_PROMO_N); n += 1
        else
          buf(n) = encMove(from, to1, KIND_QUIET); n += 1
          if r == startRank then
            val to2 = idx(f, r + fwd * 2)
            if isEmpty(arr(to2)) then
              buf(n) = encMove(from, to2, KIND_QUIET); n += 1

      var di = 0
      while di < 2 do
        val nf = f + (if di == 0 then -1 else 1)
        if nf >= 0 && nf < 8 then
          val to  = idx(nf, r1)
          val tgt = arr(to)
          if !isEmpty(tgt) && isWhitePiece(tgt) != isWhite then
            if r1 == promoRank then
              buf(n) = encMove(from, to, KIND_PROMO_Q); n += 1
              buf(n) = encMove(from, to, KIND_PROMO_R); n += 1
              buf(n) = encMove(from, to, KIND_PROMO_B); n += 1
              buf(n) = encMove(from, to, KIND_PROMO_N); n += 1
            else
              buf(n) = encMove(from, to, KIND_CAPTURE); n += 1
        di += 1

    ctx.enPassantSquare.foreach { epSq =>
      val epI = idx(epSq.file.ordinal, epSq.rank.ordinal)
      val epF = fileOf(epI); val epR = rankOf(epI)
      if epR == r1 && (epF == f - 1 || epF == f + 1) then
        buf(n) = encMove(from, epI, KIND_EP); n += 1
    }
    n

  // ─── Decode integer move word → API Move ─────────────────────────────────

  private def decodeMoveToApi(m: Int): Move =
    val fromSq = toSquare(moveFrom(m)); val toSq = toSquare(moveTo(m))
    moveKind(m) match
      case KIND_QUIET   => Move(fromSq, toSq)
      case KIND_CAPTURE => Move(fromSq, toSq, MoveType.Normal(isCapture = true))
      case KIND_EP      => Move(fromSq, toSq, MoveType.EnPassant)
      case KIND_CASTLEK => Move(fromSq, toSq, MoveType.CastleKingside)
      case KIND_CASTLEQ => Move(fromSq, toSq, MoveType.CastleQueenside)
      case KIND_PROMO_Q => Move(fromSq, toSq, MoveType.Promotion(PromotionPiece.Queen))
      case KIND_PROMO_R => Move(fromSq, toSq, MoveType.Promotion(PromotionPiece.Rook))
      case KIND_PROMO_B => Move(fromSq, toSq, MoveType.Promotion(PromotionPiece.Bishop))
      case _            => Move(fromSq, toSq, MoveType.Promotion(PromotionPiece.Knight))

  // ─── Public RuleSet API ───────────────────────────────────────────────────

  override def candidateMoves(context: GameContext)(square: Square): List[Move] =
    val arr = new Array[Int](64)
    fillBoard(context.board, arr)
    val sqI   = idx(square.file.ordinal, square.rank.ordinal)
    val piece = arr(sqI)
    if isEmpty(piece) || isWhitePiece(piece) != (context.turn == Color.White) then return Nil
    val buf = new Array[Int](64)
    val n   = generatePiece(arr, sqI, pieceType(piece), context.turn == Color.White, context, buf, 0)
    (0 until n).map(i => decodeMoveToApi(buf(i))).toList

  override def legalMoves(context: GameContext)(square: Square): List[Move] =
    val arr     = tlBoard.get(); fillBoard(context.board, arr)
    val sqI     = idx(square.file.ordinal, square.rank.ordinal)
    val piece   = arr(sqI)
    val isWhite = context.turn == Color.White
    if isEmpty(piece) || isWhitePiece(piece) != isWhite then return Nil
    val buf    = tlMoves.get()
    val n      = generatePiece(arr, sqI, pieceType(piece), isWhite, context, buf, 0)
    val result = new scala.collection.mutable.ListBuffer[Move]()
    var i      = 0
    while i < n do
      if !leavesKingInCheck(arr, buf(i), isWhite) then result += decodeMoveToApi(buf(i))
      i += 1
    result.toList

  override def allLegalMoves(context: GameContext): List[Move] =
    val arr     = tlBoard.get(); fillBoard(context.board, arr)
    val isWhite = context.turn == Color.White
    val buf     = tlMoves.get()
    val n       = generateAll(arr, isWhite, context, buf)
    val result  = new scala.collection.mutable.ListBuffer[Move]()
    var i       = 0
    while i < n do
      if !leavesKingInCheck(arr, buf(i), isWhite) then result += decodeMoveToApi(buf(i))
      i += 1
    result.toList

  override def isCheck(context: GameContext): Boolean =
    val arr     = tlBoard.get(); fillBoard(context.board, arr)
    val isWhite = context.turn == Color.White
    val kingSq  = findKing(arr, isWhite)
    kingSq >= 0 && isAttackedByColor(arr, kingSq, !isWhite)

  override def isCheckmate(context: GameContext): Boolean =
    isCheck(context) && allLegalMoves(context).isEmpty

  override def isStalemate(context: GameContext): Boolean =
    !isCheck(context) && allLegalMoves(context).isEmpty

  override def isInsufficientMaterial(context: GameContext): Boolean =
    insufficientMaterial(context.board)

  override def isFiftyMoveRule(context: GameContext): Boolean =
    context.halfMoveClock >= 100

  override def isThreefoldRepetition(context: GameContext): Boolean =
    val currentPosition = Position(context.board, context.turn, context.castlingRights, context.enPassantSquare)
    countPositionOccurrences(context, currentPosition) >= 3

  // ─── applyMove (immutable GameContext update — acceptable for real moves) ─

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

  // ─── Move application helpers ─────────────────────────────────────────────

  private def applyCastle(board: Board, color: Color, kingside: Boolean): Board =
    val rank = if color == Color.White then Rank.R1 else Rank.R8
    val (kingFrom, kingTo, rookFrom, rookTo) =
      if kingside then (Square(File.E, rank), Square(File.G, rank), Square(File.H, rank), Square(File.F, rank))
      else (Square(File.E, rank), Square(File.C, rank), Square(File.A, rank), Square(File.D, rank))
    val king = board.pieceAt(kingFrom).getOrElse(Piece(color, PieceType.King))
    val rook = board.pieceAt(rookFrom).getOrElse(Piece(color, PieceType.Rook))
    board.removed(kingFrom).removed(rookFrom).updated(kingTo, king).updated(rookTo, rook)

  private def applyEnPassant(board: Board, move: Move): Board =
    val capturedSquare = Square(move.to.file, move.from.rank)
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

    val whiteKingsideRook = Square(File.H, Rank.R1); val whiteQueensideRook = Square(File.A, Rank.R1)
    val blackKingsideRook = Square(File.H, Rank.R8); val blackQueensideRook = Square(File.A, Rank.R8)

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

    move.to match
      case `whiteKingsideRook`  => afterRookMove.revokeKingSide(Color.White)
      case `whiteQueensideRook` => afterRookMove.revokeQueenSide(Color.White)
      case `blackKingsideRook`  => afterRookMove.revokeKingSide(Color.Black)
      case `blackQueensideRook` => afterRookMove.revokeQueenSide(Color.Black)
      case _                    => afterRookMove

  private def computeEnPassantSquare(board: Board, move: Move): Option[Square] =
    val isDoublePawnPush = board.pieceAt(move.from).exists(_.pieceType == PieceType.Pawn) &&
      math.abs(move.to.rank.ordinal - move.from.rank.ordinal) == 2
    if isDoublePawnPush then
      val epRankOrd = (move.from.rank.ordinal + move.to.rank.ordinal) / 2
      Some(Square(move.from.file, Rank.values(epRankOrd)))
    else None

  // ─── Insufficient material ────────────────────────────────────────────────

  private def squareColor(sq: Square): Int = (sq.file.ordinal + sq.rank.ordinal) % 2

  private def insufficientMaterial(board: Board): Boolean =
    val nonKings = board.pieces.toList.filter { case (_, p) => p.pieceType != PieceType.King }
    nonKings match
      case Nil                                                                                => true
      case List((_, p)) if p.pieceType == PieceType.Bishop || p.pieceType == PieceType.Knight => true
      case bishops if bishops.forall { case (_, p) => p.pieceType == PieceType.Bishop } =>
        bishops.map { case (sq, _) => squareColor(sq) }.distinct.sizeIs == 1
      case _ => false

  // ─── Threefold repetition ─────────────────────────────────────────────────

  private case class Position(
      board: Board,
      turn: Color,
      castlingRights: CastlingRights,
      enPassantSquare: Option[Square],
  )

  private def countPositionOccurrences(context: GameContext, target: Position): Int =
    try
      val initialCtx = GameContext(
        board = context.initialBoard,
        turn = Color.White,
        castlingRights = CastlingRights.Initial,
        enPassantSquare = None,
        halfMoveClock = 0,
        moves = List.empty,
        initialBoard = context.initialBoard,
      )

      def positionOf(ctx: GameContext): Position =
        Position(ctx.board, ctx.turn, ctx.castlingRights, ctx.enPassantSquare)

      val initialCount = if positionOf(initialCtx) == target then 1 else 0

      context.moves
        .foldLeft((initialCtx, initialCount)) { case ((tempCtx, count), move) =>
          val nextCtx   = applyMove(tempCtx)(move)
          val nextCount = if positionOf(nextCtx) == target then count + 1 else count
          (nextCtx, nextCount)
        }
        ._2
    catch case _: Exception => 1

  override def postMoveStatus(context: GameContext): PostMoveStatus =
    PostMoveStatus(
      isCheckmate = isCheckmate(context),
      isStalemate = isStalemate(context),
      isInsufficientMaterial = isInsufficientMaterial(context),
      isCheck = isCheck(context),
      isThreefoldRepetition = isThreefoldRepetition(context),
    )
