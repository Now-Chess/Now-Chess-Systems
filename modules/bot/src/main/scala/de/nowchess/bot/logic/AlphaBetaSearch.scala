package de.nowchess.bot.logic

import de.nowchess.api.board.PieceType
import de.nowchess.api.game.GameContext
import de.nowchess.api.move.{Move, MoveType}
import de.nowchess.bot.ai.Evaluation
import de.nowchess.bot.util.ZobristHash
import de.nowchess.rules.RuleSet
import de.nowchess.rules.sets.DefaultRules
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

final class AlphaBetaSearch(
    rules: RuleSet = DefaultRules,
    tt: TranspositionTable = TranspositionTable(),
    weights: Evaluation,
    numThreads: Int = Runtime.getRuntime.availableProcessors,
):

  private val INF                  = Int.MaxValue / 2
  private val MAX_QUIESCENCE_PLY   = 64
  private val NULL_MOVE_R          = 2
  private val ASPIRATION_DELTA     = 50
  private val ASPIRATION_DELTA_MAX = 150
  private val TIME_CHECK_FREQUENCY = 1000
  private val FUTILITY_MARGIN      = 100
  private val CHECK_EXTENSION      = 1

  private val timeStartMs = AtomicLong(0L)
  private val timeLimitMs = AtomicLong(0L)
  private val nodeCount   = AtomicInteger(0)
  private val ordering    = MoveOrdering.OrderingContext()

  private final case class QuiescenceNode(
      context: GameContext,
      ply: Int,
      alpha: Int,
      beta: Int,
      hash: Long,
  )

  /** Return the best move for the side to move, searching to maxDepth plies. Uses iterative deepening with aspiration
    * windows.
    */
  def bestMove(context: GameContext, maxDepth: Int): Option[Move] =
    bestMove(context, maxDepth, Set.empty)

  def bestMove(context: GameContext, maxDepth: Int, excludedRootMoves: Set[Move]): Option[Move] =
    tt.clear()
    ordering.clear()
    weights.initAccumulator(context)
    timeStartMs.set(System.currentTimeMillis)
    timeLimitMs.set(Long.MaxValue / 4)
    nodeCount.set(0)
    val rootHash = ZobristHash.hash(context)
    (1 to maxDepth)
      .foldLeft((None: Option[Move], 0)) { case ((bestSoFar, prevScore), depth) =>
        val (alpha, beta) =
          if depth == 1 then (-INF, INF) else (prevScore - ASPIRATION_DELTA, prevScore + ASPIRATION_DELTA)
        val (score, move) = searchWithAspiration(
          context,
          depth,
          alpha,
          beta,
          ASPIRATION_DELTA,
          rootHash,
          excludedRootMoves,
        )
        (move.orElse(bestSoFar), score)
      }
      ._1

  /** Return the best move for the side to move within a time budget (ms). Uses iterative deepening, stopping when time
    * runs out.
    */
  def bestMoveWithTime(context: GameContext, timeBudgetMs: Long): Option[Move] =
    bestMoveWithTime(context, timeBudgetMs, Set.empty)

  def bestMoveWithTime(context: GameContext, timeBudgetMs: Long, excludedRootMoves: Set[Move]): Option[Move] =
    tt.clear()
    ordering.clear()
    weights.initAccumulator(context)
    timeStartMs.set(System.currentTimeMillis)
    timeLimitMs.set(timeBudgetMs)
    nodeCount.set(0)
    val rootHash = ZobristHash.hash(context)

    @scala.annotation.tailrec
    def loop(bestSoFar: Option[Move], prevScore: Int, depth: Int): Option[Move] =
      if isOutOfTime then bestSoFar
      else
        val (alpha, beta) =
          if depth == 1 then (-INF, INF) else (prevScore - ASPIRATION_DELTA, prevScore + ASPIRATION_DELTA)
        val (score, move) = searchWithAspiration(
          context,
          depth,
          alpha,
          beta,
          ASPIRATION_DELTA,
          rootHash,
          excludedRootMoves,
        )
        loop(move.orElse(bestSoFar), score, depth + 1)

    loop(None, 0, 1)

  private def isOutOfTime: Boolean =
    System.currentTimeMillis - timeStartMs.get >= timeLimitMs.get

  private def searchWithAspiration(
      context: GameContext,
      depth: Int,
      alpha: Int,
      beta: Int,
      initialWindow: Int,
      rootHash: Long,
      excludedRootMoves: Set[Move],
  ): (Int, Option[Move]) =
    val state = SearchState(rootHash, Map(rootHash -> 1))

    @scala.annotation.tailrec
    def loop(currentAlpha: Int, currentBeta: Int, delta: Int, attempt: Int): (Int, Option[Move]) =
      if attempt >= 3 || attempt >= depth then search(context, depth, 0, Window(-INF, INF), state, excludedRootMoves)
      else
        val (score, move) = search(context, depth, 0, Window(currentAlpha, currentBeta), state, excludedRootMoves)
        if score > currentAlpha && score < currentBeta then (score, move)
        else if score <= currentAlpha then
          loop(score - delta, currentBeta, math.min(delta * 2, ASPIRATION_DELTA_MAX), attempt + 1)
        else loop(currentAlpha, score + delta, math.min(delta * 2, ASPIRATION_DELTA_MAX), attempt + 1)

    loop(alpha, beta, initialWindow, 0)

  private def hasNonPawnMaterial(context: GameContext): Boolean =
    context.board.pieces.values.exists { piece =>
      piece.color == context.turn &&
      piece.pieceType != PieceType.Pawn &&
      piece.pieceType != PieceType.King
    }

  private def nullMoveContext(context: GameContext): GameContext =
    context.withTurn(context.turn.opposite).withEnPassantSquare(None)

  private def tryNullMove(
      context: GameContext,
      depth: Int,
      ply: Int,
      beta: Int,
      state: SearchState,
      excludedRootMoves: Set[Move],
  ): Option[Int] =
    val nullCtx        = nullMoveContext(context)
    val nullState      = state.advance(ZobristHash.hash(nullCtx))
    val reductionDepth = math.max(0, depth - 1 - NULL_MOVE_R)
    weights.copyAccumulator(ply, ply + 1)
    val (score, _) = search(nullCtx, reductionDepth, ply + 1, Window(-beta, -beta + 1), nullState, excludedRootMoves)
    if -score >= beta then Some(beta) else None

  /** Negamax alpha-beta search returning (score, best move). */
  private def search(
      context: GameContext,
      depth: Int,
      ply: Int,
      window: Window,
      state: SearchState,
      excludedRootMoves: Set[Move],
  ): (Int, Option[Move]) =
    val params = SearchParams(context, depth, ply, window, state, excludedRootMoves)
    searchNode(params)

  private def searchNode(params: SearchParams): (Int, Option[Move]) =
    val count = nodeCount.incrementAndGet()
    immediateSearchResult(params, count).getOrElse {
      val legalMoves = rules.allLegalMoves(params.context)
      terminalSearchResult(params, legalMoves).getOrElse(searchDeeper(params, legalMoves))
    }

  private def immediateSearchResult(
      params: SearchParams,
      count: Int,
  ): Option[(Int, Option[Move])] =
    if count % TIME_CHECK_FREQUENCY == 0 && isOutOfTime then
      Some((weights.evaluateAccumulator(params.ply, params.context, params.state.hash), None))
    else if params.state.repetitions.getOrElse(params.state.hash, 0) >= 3 then Some((weights.DRAW_SCORE, None))
    else ttCutoff(params)

  private def ttCutoff(params: SearchParams): Option[(Int, Option[Move])] =
    tt.probe(params.state.hash).filter(_.depth >= params.depth).flatMap { entry =>
      entry.flag match
        case TTFlag.Exact => Some((entry.score, entry.bestMove))
        case TTFlag.Lower =>
          val newAlpha = math.max(params.window.alpha, entry.score)
          Option.when(newAlpha >= params.window.beta)((entry.score, entry.bestMove))
        case TTFlag.Upper =>
          val newBeta = math.min(params.window.beta, entry.score)
          Option.when(params.window.alpha >= newBeta)((entry.score, entry.bestMove))
    }

  private def terminalSearchResult(
      params: SearchParams,
      legalMoves: List[Move],
  ): Option[(Int, Option[Move])] =
    if legalMoves.isEmpty then
      Some(
        (
          if rules.isCheckmate(params.context) then -(weights.CHECKMATE_SCORE - params.ply) else weights.DRAW_SCORE,
          None,
        ),
      )
    else if rules.isInsufficientMaterial(params.context) || rules.isFiftyMoveRule(params.context) then
      Some((weights.DRAW_SCORE, None))
    else if params.depth == 0 then
      Some((quiescence(params.context, params.ply, params.window.alpha, params.window.beta, params.state.hash), None))
    else None

  private def searchDeeper(
      params: SearchParams,
      legalMoves: List[Move],
  ): (Int, Option[Move]) =
    val nullResult =
      Option
        .when(canTryNullMove(params))(
          tryNullMove(
            params.context,
            params.depth,
            params.ply,
            params.window.beta,
            params.state,
            params.excludedRootMoves,
          ),
        )
        .flatten

    nullResult.map((_, None)).getOrElse {
      val ttBest  = tt.probe(params.state.hash).flatMap(_.bestMove)
      val ordered = MoveOrdering.sort(params.context, legalMoves, ttBest, params.ply, ordering)
      searchSequential(
        params.context,
        params.depth,
        params.ply,
        params.window,
        ordered,
        params.state,
        params.excludedRootMoves,
      )
    }

  private def canTryNullMove(params: SearchParams): Boolean =
    params.depth >= 3 &&
      !rules.isCheck(params.context) &&
      hasNonPawnMaterial(params.context)

  private def isQuietMove(context: GameContext, move: Move): Boolean =
    !isCapture(context, move) &&
      move.moveType != MoveType.CastleKingside &&
      move.moveType != MoveType.CastleQueenside

  private def scoreMove(
      child: GameContext,
      childState: SearchState,
      params: SearchParams,
      extension: Int,
      reduction: Int,
      a: Int,
  ): Int =
    val betaNeg = -params.window.beta
    if reduction > 0 then
      val (rs, _) = search(
        child,
        math.max(0, params.depth - 1 - reduction + extension),
        params.ply + 1,
        Window(-a - 1, -a),
        childState,
        params.excludedRootMoves,
      )
      val s = -rs
      if s > a then
        val (fs, _) = search(
          child,
          math.max(0, params.depth - 1 + extension),
          params.ply + 1,
          Window(betaNeg, -a),
          childState,
          params.excludedRootMoves,
        )
        -fs
      else s
    else
      val (rs, _) = search(
        child,
        math.max(0, params.depth - 1 + extension),
        params.ply + 1,
        Window(betaNeg, -a),
        childState,
        params.excludedRootMoves,
      )
      -rs

  private def evalSingleMove(
      move: Move,
      moveNumber: Int,
      a: Int,
      params: SearchParams,
  ): Option[(Int, Boolean)] =
    val skipRoot = params.ply == 0 && params.excludedRootMoves.contains(move)
    val isQuiet  = isQuietMove(params.context, move)
    val futility = params.depth == 1 && isQuiet && moveNumber > 2 &&
      weights.evaluateAccumulator(params.ply, params.context, params.state.hash) + FUTILITY_MARGIN < params.window.alpha
    if skipRoot || futility then None
    else
      val child     = rules.applyMove(params.context)(move)
      val childHash = ZobristHash.nextHash(params.context, params.state.hash, move, child)
      weights.pushAccumulator(params.ply + 1, move, params.context, child)
      val childState = params.state.advance(childHash)
      val extension  = if rules.isCheck(child) then CHECK_EXTENSION else 0
      val reduction  = if moveNumber > 4 && params.depth >= 3 && isQuiet then 1 else 0
      Some((scoreMove(child, childState, params, extension, reduction, a), isQuiet))

  private def recordCutoff(move: Move, depth: Int, ply: Int): Unit =
    ordering.addHistory(
      move.from.rank.ordinal * 8 + move.from.file.ordinal,
      move.to.rank.ordinal * 8 + move.to.file.ordinal,
      depth * depth,
    )
    ordering.addKillerMove(ply, move)

  @scala.annotation.tailrec
  private def searchLoop(
      idx: Int,
      moveNumber: Int,
      acc: LoopAcc,
      params: SearchParams,
      ordered: List[Move],
  ): (Option[Move], Int, Boolean) =
    if idx >= ordered.length then (acc.bestMove, acc.bestScore, false)
    else
      val move = ordered(idx)
      evalSingleMove(move, moveNumber, acc.a, params) match
        case None => searchLoop(idx + 1, moveNumber + 1, acc, params, ordered)
        case Some((score, isQuiet)) =>
          val newAcc = LoopAcc(
            if score > acc.bestScore then Some(move) else acc.bestMove,
            math.max(acc.bestScore, score),
            math.max(acc.a, score),
          )
          if newAcc.a >= params.window.beta then
            if isQuiet then recordCutoff(move, params.depth, params.ply)
            (newAcc.bestMove, newAcc.bestScore, true)
          else searchLoop(idx + 1, moveNumber + 1, newAcc, params, ordered)

  private def searchSequential(
      context: GameContext,
      depth: Int,
      ply: Int,
      window: Window,
      ordered: List[Move],
      state: SearchState,
      excludedRootMoves: Set[Move],
  ): (Int, Option[Move]) =
    val params                        = SearchParams(context, depth, ply, window, state, excludedRootMoves)
    val (bestMove, bestScore, cutoff) = searchLoop(0, 0, LoopAcc(None, -INF, window.alpha), params, ordered)
    val flag =
      if cutoff then TTFlag.Lower
      else if bestScore <= window.alpha then TTFlag.Upper
      else TTFlag.Exact
    tt.store(TTEntry(state.hash, depth, bestScore, flag, bestMove))
    (bestScore, bestMove)

  /** Quiescence search: only captures until position is quiet. */
  private def quiescence(
      context: GameContext,
      ply: Int,
      alpha: Int,
      beta: Int,
      hash: Long,
  ): Int =
    quiescenceNode(QuiescenceNode(context, ply, alpha, beta, hash))

  private def quiescenceNode(node: QuiescenceNode): Int =
    val inCheck  = rules.isCheck(node.context)
    val standPat = if inCheck then -INF else weights.evaluateAccumulator(node.ply, node.context, node.hash)

    if !inCheck && standPat >= node.beta then node.beta
    else if node.ply >= MAX_QUIESCENCE_PLY then quiescenceAtDepthLimit(node, inCheck, standPat)
    else
      val moves = tacticalMoves(node.context, inCheck)
      if inCheck && moves.isEmpty then -(weights.CHECKMATE_SCORE - node.ply)
      else
        val ordered = MoveOrdering.sort(node.context, moves, None)
        val a0      = if inCheck then node.alpha else math.max(node.alpha, standPat)
        quiescenceLoop(node, ordered, 0, a0)

  private def quiescenceAtDepthLimit(node: QuiescenceNode, inCheck: Boolean, standPat: Int): Int =
    if inCheck then weights.evaluateAccumulator(node.ply, node.context, node.hash) else standPat

  private def tacticalMoves(context: GameContext, inCheck: Boolean): List[Move] =
    val allMoves = rules.allLegalMoves(context)
    if inCheck then allMoves else allMoves.filter(m => isCapture(context, m))

  @scala.annotation.tailrec
  private def quiescenceLoop(
      node: QuiescenceNode,
      ordered: List[Move],
      idx: Int,
      a: Int,
  ): Int =
    if idx >= ordered.length then a
    else
      val move      = ordered(idx)
      val child     = rules.applyMove(node.context)(move)
      val childHash = ZobristHash.nextHash(node.context, node.hash, move, child)
      weights.pushAccumulator(node.ply + 1, move, node.context, child)
      val score = -quiescence(child, node.ply + 1, -node.beta, -a, childHash)
      if score >= node.beta then node.beta
      else quiescenceLoop(node, ordered, idx + 1, math.max(a, score))

  private def isCapture(context: GameContext, move: Move): Boolean = move.moveType match
    case MoveType.Normal(true) => true
    case MoveType.EnPassant    => true
    case MoveType.Promotion(_) => context.board.pieceAt(move.to).exists(_.color != context.turn)
    case _                     => false
