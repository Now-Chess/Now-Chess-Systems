package de.nowchess.bot.bots.nnue

import de.nowchess.api.board.{Board, Color, Piece, PieceType, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}

class NNUE(model: NbaiModel):

  private val HALF_SIZE     = 49152                     // 64 king-squares × 12 piece-types × 64 piece-squares
  private val featureSize   = model.layers(0).inputSize // 98304 (= HALF_SIZE * 2) for king-relative
  private val accSize       = model.layers(0).outputSize
  private val validateAccum = sys.env.contains("NNUE_VALIDATE")

  // Column-major L1 weights: l1WeightsT(featureIdx * accSize + outputIdx)
  private val l1WeightsT: Array[Float] =
    val w = model.weights(0).weights
    val t = new Array[Float](featureSize * accSize)
    for j <- 0 until featureSize; i <- 0 until accSize do t(j * accSize + i) = w(i * featureSize + j)
    t

  // ── Accumulator stack ────────────────────────────────────────────────────

  private val MAX_PLY                      = 128
  private val l1Stack: Array[Array[Float]] = Array.fill(MAX_PLY + 1)(new Array[Float](accSize))

  private val evalBuffers: Array[Array[Float]] = model.layers.init.map(l => new Array[Float](l.outputSize))

  // ── Eval cache ───────────────────────────────────────────────────────────

  private val EVAL_CACHE_MASK = (1 << 18) - 1L
  private val evalCacheHashes = new Array[Long](1 << 18)
  private val evalCacheScores = new Array[Int](1 << 18)

  // ── Feature helpers ──────────────────────────────────────────────────────

  private def squareNum(sq: Square): Int = sq.rank.ordinal * 8 + sq.file.ordinal

  // Mirror square vertically (rank 0 ↔ rank 7) for the perspective flip
  private def flipSqNum(sqNum: Int): Int = (7 - sqNum / 8) * 8 + sqNum % 8

  private def pieceIdx(piece: Piece): Int =
    if piece.color == Color.White then 6 + piece.pieceType.ordinal else piece.pieceType.ordinal

  // White-king perspective: index in [0, HALF_SIZE)
  private def featureIdxWhite(piece: Piece, sqNum: Int, wkSq: Int): Int =
    wkSq * 768 + pieceIdx(piece) * 64 + sqNum

  // Black-king perspective: index in [HALF_SIZE, featureSize)
  private def featureIdxBlack(piece: Piece, sqNum: Int, bkSq: Int): Int =
    HALF_SIZE + bkSq * 768 + pieceIdx(piece) * 64 + sqNum

  private def wkSqOf(board: Board): Int =
    board.pieces
      .collectFirst { case (sq, p) if p.pieceType == PieceType.King && p.color == Color.White => squareNum(sq) }
      .getOrElse(0)

  private def bkSqOf(board: Board): Int =
    board.pieces
      .collectFirst { case (sq, p) if p.pieceType == PieceType.King && p.color == Color.Black => squareNum(sq) }
      .getOrElse(0)

  private def addColumn(l1Pre: Array[Float], featureIdx: Int): Unit =
    val offset = featureIdx * accSize
    for i <- 0 until accSize do l1Pre(i) += l1WeightsT(offset + i)

  private def subtractColumn(l1Pre: Array[Float], featureIdx: Int): Unit =
    val offset = featureIdx * accSize
    for i <- 0 until accSize do l1Pre(i) -= l1WeightsT(offset + i)

  private def addPiece(l1: Array[Float], piece: Piece, sqNum: Int, wkSq: Int, bkSq: Int): Unit =
    addColumn(l1, featureIdxWhite(piece, sqNum, wkSq))
    addColumn(l1, featureIdxBlack(piece, sqNum, bkSq))

  private def removePiece(l1: Array[Float], piece: Piece, sqNum: Int, wkSq: Int, bkSq: Int): Unit =
    subtractColumn(l1, featureIdxWhite(piece, sqNum, wkSq))
    subtractColumn(l1, featureIdxBlack(piece, sqNum, bkSq))

  // ── Accumulator init ─────────────────────────────────────────────────────

  def initAccumulator(board: Board): Unit =
    val wkSq = wkSqOf(board)
    val bkSq = bkSqOf(board)
    System.arraycopy(model.weights(0).bias, 0, l1Stack(0), 0, accSize)
    for (sq, piece) <- board.pieces do addPiece(l1Stack(0), piece, squareNum(sq), wkSq, bkSq)

  // ── Accumulator push (incremental updates) ───────────────────────────────

  def pushAccumulator(childPly: Int, move: Move, parentBoard: Board, childBoard: Board): Unit =
    System.arraycopy(l1Stack(childPly - 1), 0, l1Stack(childPly), 0, accSize)
    if isKingMove(move, parentBoard) then recomputeAccumulatorInto(l1Stack(childPly), childBoard)
    else applyNonKingDelta(l1Stack(childPly), move, parentBoard)

  private def isKingMove(move: Move, board: Board): Boolean =
    move.moveType == MoveType.CastleKingside ||
      move.moveType == MoveType.CastleQueenside ||
      board.pieceAt(move.from).exists(_.pieceType == PieceType.King)

  def copyAccumulator(parentPly: Int, childPly: Int): Unit =
    System.arraycopy(l1Stack(parentPly), 0, l1Stack(childPly), 0, accSize)

  def recomputeAccumulator(ply: Int, board: Board): Unit =
    recomputeAccumulatorInto(l1Stack(ply), board)

  private def recomputeAccumulatorInto(l1: Array[Float], board: Board): Unit =
    val wkSq = wkSqOf(board)
    val bkSq = bkSqOf(board)
    System.arraycopy(model.weights(0).bias, 0, l1, 0, accSize)
    for (sq, piece) <- board.pieces do addPiece(l1, piece, squareNum(sq), wkSq, bkSq)

  def validateAccumulator(ply: Int, board: Board): Boolean =
    val expected = new Array[Float](accSize)
    val wkSq     = wkSqOf(board)
    val bkSq     = bkSqOf(board)
    System.arraycopy(model.weights(0).bias, 0, expected, 0, accSize)
    for (sq, piece) <- board.pieces do addPiece(expected, piece, squareNum(sq), wkSq, bkSq)
    val actual = l1Stack(ply)
    (0 until accSize).forall(i => math.abs(actual(i) - expected(i)) < 0.001f)

  // ── Non-king incremental deltas ──────────────────────────────────────────

  private def applyNonKingDelta(l1: Array[Float], move: Move, board: Board): Unit =
    val wkSq = wkSqOf(board)
    val bkSq = bkSqOf(board)
    move.moveType match
      case MoveType.Normal(_)    => applyNormalDelta(l1, move, board, wkSq, bkSq)
      case MoveType.EnPassant    => applyEnPassantDelta(l1, move, board, wkSq, bkSq)
      case MoveType.Promotion(p) => applyPromotionDelta(l1, move, p, board, wkSq, bkSq)
      case _                     => () // king moves handled before this point

  private def applyNormalDelta(l1: Array[Float], move: Move, board: Board, wkSq: Int, bkSq: Int): Unit =
    board.pieceAt(move.from).foreach { mover =>
      val fromNum = squareNum(move.from)
      val toNum   = squareNum(move.to)
      removePiece(l1, mover, fromNum, wkSq, bkSq)
      board.pieceAt(move.to).foreach(cap => removePiece(l1, cap, toNum, wkSq, bkSq))
      addPiece(l1, mover, toNum, wkSq, bkSq)
    }

  private def applyEnPassantDelta(l1: Array[Float], move: Move, board: Board, wkSq: Int, bkSq: Int): Unit =
    board.pieceAt(move.from).foreach { pawn =>
      val capturedSq = Square(move.to.file, move.from.rank)
      removePiece(l1, pawn, squareNum(move.from), wkSq, bkSq)
      board.pieceAt(capturedSq).foreach(cap => removePiece(l1, cap, squareNum(capturedSq), wkSq, bkSq))
      addPiece(l1, pawn, squareNum(move.to), wkSq, bkSq)
    }

  private def applyPromotionDelta(
      l1: Array[Float],
      move: Move,
      promo: PromotionPiece,
      board: Board,
      wkSq: Int,
      bkSq: Int,
  ): Unit =
    board.pieceAt(move.from).foreach { pawn =>
      val toNum = squareNum(move.to)
      removePiece(l1, pawn, squareNum(move.from), wkSq, bkSq)
      board.pieceAt(move.to).foreach(cap => removePiece(l1, cap, toNum, wkSq, bkSq))
      addPiece(l1, Piece(pawn.color, promotedType(promo)), toNum, wkSq, bkSq)
    }

  private def promotedType(promo: PromotionPiece): PieceType = promo match
    case PromotionPiece.Knight => PieceType.Knight
    case PromotionPiece.Bishop => PieceType.Bishop
    case PromotionPiece.Rook   => PieceType.Rook
    case PromotionPiece.Queen  => PieceType.Queen

  // ── Evaluation from accumulator ──────────────────────────────────────────

  def evaluateAtPly(ply: Int, turn: Color, hash: Long): Int =
    val idx = (hash & EVAL_CACHE_MASK).toInt
    if evalCacheHashes(idx) == hash then evalCacheScores(idx)
    else
      val score = runL2toOutput(l1Stack(ply), turn)
      evalCacheHashes(idx) = hash
      evalCacheScores(idx) = score
      score

  def evaluateAtPlyWithValidation(ply: Int, turn: Color, hash: Long, board: Board): Int =
    if validateAccum && ply > 0 && ply % 10 != 0 then
      val isValid = validateAccumulator(ply, board)
      if !isValid then System.err.println(s"WARNING: NNUE accumulator diverged at ply $ply")
    evaluateAtPly(ply, turn, hash)

  private def runL2toOutput(l1Pre: Array[Float], turn: Color): Int =
    val l1ReLU = evalBuffers(0)
    for i <- 0 until accSize do l1ReLU(i) = if l1Pre(i) > 0f then l1Pre(i) else 0f

    val finalInput =
      (1 until model.layers.length - 1).foldLeft(l1ReLU) { (input, i) =>
        val lw  = model.weights(i)
        val out = evalBuffers(i)
        val ld  = model.layers(i)
        runDenseReLU(input, ld.inputSize, lw.weights, lw.bias, out, ld.outputSize)
        out
      }

    val lastIdx = model.layers.length - 1
    val output  = runOutputLayer(finalInput, model.layers(lastIdx).inputSize, model.weights(lastIdx))
    scoreFromOutput(output, turn)

  private def runDenseReLU(
      input: Array[Float],
      inSize: Int,
      weights: Array[Float],
      bias: Array[Float],
      output: Array[Float],
      outSize: Int,
  ): Unit =
    for i <- 0 until outSize do
      val sum = (0 until inSize).foldLeft(bias(i))((s, j) => s + input(j) * weights(i * inSize + j))
      output(i) = if sum > 0f then sum else 0f

  private def runOutputLayer(input: Array[Float], inSize: Int, lw: LayerWeights): Float =
    (0 until inSize).foldLeft(lw.bias(0))((sum, j) => sum + input(j) * lw.weights(j))

  private def scoreFromOutput(output: Float, turn: Color): Int =
    val cp =
      if math.abs(output) >= 0.9999f then if output > 0f then 20000 else -20000
      else
        val atanh = 0.5f * math.log((1f + output) / (1f - output)).toFloat
        (300f * atanh).toInt
    val cpFromTurn = if turn == Color.Black then -cp else cp
    math.max(-20000, math.min(20000, cpFromTurn))

  // ── Legacy full-board evaluate ────────────────────────────────────────────

  private val legacyL1 = new Array[Float](accSize)

  def evaluate(context: GameContext): Int =
    // Match training: for Black-to-move positions, mirror the board (ranks flipped,
    // colours swapped) so the model always sees from the side-to-move's perspective.
    // The scoreFromOutput negation then converts back to White's absolute perspective.
    val (wkSq, bkSq, pieces, turn) =
      if context.turn == Color.Black then
        val wk = flipSqNum(bkSqOf(context.board)) // flipped Black king → new "White" king
        val bk = flipSqNum(wkSqOf(context.board)) // flipped White king → new "Black" king
        val flipped = context.board.pieces.map { case (sq, p) =>
          (sq, Piece(p.color.opposite, p.pieceType))
        }
        (wk, bk, flipped, Color.Black) // pass Black so scoreFromOutput negates the result
      else (wkSqOf(context.board), bkSqOf(context.board), context.board.pieces, context.turn)
    System.arraycopy(model.weights(0).bias, 0, legacyL1, 0, accSize)
    for (sq, piece) <- pieces do
      val sqNum = if turn == Color.Black then flipSqNum(squareNum(sq)) else squareNum(sq)
      addPiece(legacyL1, piece, sqNum, wkSq, bkSq)
    runL2toOutput(legacyL1, turn)

  def benchmark(): Unit =
    val context    = GameContext.initial
    val iterations = 1_000_000
    for _ <- 0 until 10000 do evaluate(context)
    val startNanos = System.nanoTime()
    for _ <- 0 until iterations do evaluate(context)
    val endNanos     = System.nanoTime()
    val totalNanos   = endNanos - startNanos
    val nanosPerEval = totalNanos.toDouble / iterations
    println()
    println("=" * 60)
    println("NNUE BENCHMARK RESULTS")
    println("=" * 60)
    println(f"Iterations:        $iterations%,d")
    println(f"Total time:        ${totalNanos / 1e9}%.2f seconds")
    println(f"ns/eval:           $nanosPerEval%.2f ns")
    println(f"evals/second:      ${1e9 / nanosPerEval}%.0f evals/s")
    println("=" * 60)
    println()
