package de.nowchess.bot.bots.nnue

import de.nowchess.api.board.{Board, Color, File, Piece, PieceType, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}

class NNUE(model: NbaiModel):

  private val featureSize   = model.layers(0).inputSize
  private val accSize       = model.layers(0).outputSize
  private val validateAccum = sys.env.contains("NNUE_VALIDATE") // Enable with NNUE_VALIDATE=1

  // Column-major L1 weights for cache-friendly sparse & incremental updates.
  // l1WeightsT(featureIdx * accSize + outputIdx) = l1Weights(outputIdx * featureSize + featureIdx)
  private val l1WeightsT: Array[Float] =
    val w = model.weights(0).weights
    val t = new Array[Float](featureSize * accSize)
    for j <- 0 until featureSize; i <- 0 until accSize do t(j * accSize + i) = w(i * featureSize + j)
    t

  // ── Accumulator stack ────────────────────────────────────────────────────

  private val MAX_PLY                      = 128
  private val l1Stack: Array[Array[Float]] = Array.fill(MAX_PLY + 1)(new Array[Float](accSize))

  // Shared evaluation buffers: index i holds the output of layers(i) (all except the scalar output layer).
  private val evalBuffers: Array[Array[Float]] = model.layers.init.map(l => new Array[Float](l.outputSize))

  // ── Eval cache ───────────────────────────────────────────────────────────

  private val EVAL_CACHE_MASK = (1 << 18) - 1L
  private val evalCacheHashes = new Array[Long](1 << 18)
  private val evalCacheScores = new Array[Int](1 << 18)

  // ── Feature helpers ──────────────────────────────────────────────────────

  private def squareNum(sq: Square): Int = sq.rank.ordinal * 8 + sq.file.ordinal

  private def featureIndex(piece: Piece, sqNum: Int): Int =
    val colorOffset = if piece.color == Color.White then 6 else 0
    (colorOffset + piece.pieceType.ordinal) * 64 + sqNum

  private def addColumn(l1Pre: Array[Float], featureIdx: Int): Unit =
    val offset = featureIdx * accSize
    for i <- 0 until accSize do l1Pre(i) += l1WeightsT(offset + i)

  private def subtractColumn(l1Pre: Array[Float], featureIdx: Int): Unit =
    val offset = featureIdx * accSize
    for i <- 0 until accSize do l1Pre(i) -= l1WeightsT(offset + i)

  // ── Accumulator init ─────────────────────────────────────────────────────

  def initAccumulator(board: Board): Unit =
    System.arraycopy(model.weights(0).bias, 0, l1Stack(0), 0, accSize)
    for (sq, piece) <- board.pieces do addColumn(l1Stack(0), featureIndex(piece, squareNum(sq)))

  // ── Accumulator push (incremental updates) ───────────────────────────────

  def pushAccumulator(childPly: Int, move: Move, board: Board): Unit =
    System.arraycopy(l1Stack(childPly - 1), 0, l1Stack(childPly), 0, accSize)
    val l1 = l1Stack(childPly)
    move.moveType match
      case MoveType.Normal(_)                                 => applyNormalDelta(l1, move, board)
      case MoveType.EnPassant                                 => applyEnPassantDelta(l1, move, board)
      case MoveType.CastleKingside | MoveType.CastleQueenside => applyCastleDelta(l1, move, board)
      case MoveType.Promotion(p)                              => applyPromotionDelta(l1, move, p, board)

  def copyAccumulator(parentPly: Int, childPly: Int): Unit =
    System.arraycopy(l1Stack(parentPly), 0, l1Stack(childPly), 0, accSize)

  def recomputeAccumulator(ply: Int, board: Board): Unit =
    System.arraycopy(model.weights(0).bias, 0, l1Stack(ply), 0, accSize)
    for (sq, piece) <- board.pieces do addColumn(l1Stack(ply), featureIndex(piece, squareNum(sq)))

  def validateAccumulator(ply: Int, board: Board): Boolean =
    // Compute what L1 should be from scratch
    val expectedL1 = new Array[Float](accSize)
    System.arraycopy(model.weights(0).bias, 0, expectedL1, 0, accSize)
    for (sq, piece) <- board.pieces do addColumn(expectedL1, featureIndex(piece, squareNum(sq)))

    // Compare with actual L1
    val actual = l1Stack(ply)
    val maxError =
      (0 until accSize).foldLeft(0f) { (currentMax, i) =>
        val error = math.abs(actual(i) - expectedL1(i))
        math.max(currentMax, error)
      }

    maxError < 0.001f // Allow small floating-point errors

  private def applyNormalDelta(l1: Array[Float], move: Move, board: Board): Unit =
    // Extract source and destination square indices early
    val fromNum = squareNum(move.from)
    val toNum   = squareNum(move.to)

    // Get the moving piece
    board.pieceAt(move.from).foreach { mover =>
      subtractColumn(l1, featureIndex(mover, fromNum))

      // If there's a capture, subtract the captured piece
      board.pieceAt(move.to).foreach { cap =>
        subtractColumn(l1, featureIndex(cap, toNum))
      }

      // Add the piece to its new location
      addColumn(l1, featureIndex(mover, toNum))
    }

  private def applyEnPassantDelta(l1: Array[Float], move: Move, board: Board): Unit =
    board.pieceAt(move.from).foreach { pawn =>
      val capturedSq = Square(move.to.file, move.from.rank)
      subtractColumn(l1, featureIndex(pawn, squareNum(move.from)))
      board.pieceAt(capturedSq).foreach(cap => subtractColumn(l1, featureIndex(cap, squareNum(capturedSq))))
      addColumn(l1, featureIndex(pawn, squareNum(move.to)))
    }

  private def applyCastleDelta(l1: Array[Float], move: Move, board: Board): Unit =
    board.pieceAt(move.from).foreach { king =>
      val rank     = move.from.rank
      val kingside = move.moveType == MoveType.CastleKingside
      val (rookFrom, rookTo) =
        if kingside then (Square(File.H, rank), Square(File.F, rank))
        else (Square(File.A, rank), Square(File.D, rank))
      val rook = Piece(king.color, PieceType.Rook)
      subtractColumn(l1, featureIndex(king, squareNum(move.from)))
      addColumn(l1, featureIndex(king, squareNum(move.to)))
      subtractColumn(l1, featureIndex(rook, squareNum(rookFrom)))
      addColumn(l1, featureIndex(rook, squareNum(rookTo)))
    }

  private def applyPromotionDelta(l1: Array[Float], move: Move, promo: PromotionPiece, board: Board): Unit =
    board.pieceAt(move.from).foreach { pawn =>
      val toNum = squareNum(move.to)
      subtractColumn(l1, featureIndex(pawn, squareNum(move.from)))
      board.pieceAt(move.to).foreach(cap => subtractColumn(l1, featureIndex(cap, toNum)))
      addColumn(l1, featureIndex(Piece(pawn.color, promotedType(promo)), toNum))
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
    // For debugging: validate that incremental accumulator matches recomputation
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
    System.arraycopy(model.weights(0).bias, 0, legacyL1, 0, accSize)
    for (sq, piece) <- context.board.pieces do addColumn(legacyL1, featureIndex(piece, squareNum(sq)))
    runL2toOutput(legacyL1, context.turn)

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
