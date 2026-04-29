package de.nowchess.bot.bots.classic

import de.nowchess.api.board.{Color, PieceType, Square}
import de.nowchess.api.game.GameContext
import de.nowchess.bot.ai.Evaluation

object EvaluationClassic extends Evaluation:

  val CHECKMATE_SCORE: Int = 10_000_000
  val DRAW_SCORE: Int      = 0

  // Material values in centipawns (indexed by PieceType.ordinal: Pawn=0, Knight=1, Bishop=2, Rook=3, Queen=4, King=5)
  private val mgMaterial = Array(100, 325, 335, 500, 900, 20_000)
  private val egMaterial = Array(110, 310, 310, 530, 1_000, 20_000)

  private val TEMPO_BONUS: Int = 10

  // Piece-square tables (Simplified Evaluation Function, Michniewski)
  // Indexed by squareIndex = rank.ordinal * 8 + file.ordinal
  // White's perspective: rank 0 = home (r1), rank 7 = back rank (r8)
  // Black is vertically mirrored

  private val mgPawnTable: Array[Int] = Array(
    0, 0, 0, 0, 0, 0, 0, 0, 50, 50, 50, 50, 50, 50, 50, 50, 10, 10, 20, 30, 30, 20, 10, 10, 5, 5, 10, 25, 25, 10, 5, 5,
    0, 0, 0, 20, 20, 0, 0, 0, 5, -5, -10, 0, 0, -10, -5, 5, 5, 10, 10, -20, -20, 10, 10, 5, 0, 0, 0, 0, 0, 0, 0, 0,
  )

  private val egPawnTable: Array[Int] = Array(
    0, 0, 0, 0, 0, 0, 0, 0, 70, 70, 70, 70, 70, 70, 70, 70, 40, 40, 40, 40, 40, 40, 40, 40, 30, 30, 30, 30, 30, 30, 30,
    30, 20, 20, 20, 20, 20, 20, 20, 20, 10, 10, 10, 10, 10, 10, 10, 10, 5, 5, 5, 5, 5, 5, 5, 5, 0, 0, 0, 0, 0, 0, 0, 0,
  )

  private val mgKnightTable: Array[Int] = Array(
    -50, -40, -30, -30, -30, -30, -40, -50, -40, -20, 0, 0, 0, 0, -20, -40, -30, 0, 10, 15, 15, 10, 0, -30, -30, 5, 15,
    20, 20, 15, 5, -30, -30, 0, 15, 20, 20, 15, 0, -30, -30, 5, 10, 15, 15, 10, 5, -30, -40, -20, 0, 5, 5, 0, -20, -40,
    -50, -40, -30, -30, -30, -30, -40, -50,
  )

  private val egKnightTable: Array[Int] = Array(
    -30, -20, -10, -10, -10, -10, -20, -30, -20, 0, 5, 5, 5, 5, 0, -20, -10, 5, 15, 20, 20, 15, 5, -10, -10, 5, 20, 25,
    25, 20, 5, -10, -10, 5, 20, 25, 25, 20, 5, -10, -10, 5, 15, 20, 20, 15, 5, -10, -20, 0, 5, 5, 5, 5, 0, -20, -30,
    -20, -10, -10, -10, -10, -20, -30,
  )

  private val mgBishopTable: Array[Int] = Array(
    -20, -10, -10, -10, -10, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 10, 10, 5, 0, -10, -10, 5, 5, 10, 10,
    5, 5, -10, -10, 0, 10, 10, 10, 10, 0, -10, -10, 10, 10, 10, 10, 10, 10, -10, -10, 5, 0, 0, 0, 0, 5, -10, -20, -10,
    -10, -10, -10, -10, -10, -20,
  )

  private val egBishopTable: Array[Int] = Array(
    -20, -10, -5, -5, -5, -5, -10, -20, -10, 0, 5, 5, 5, 5, 0, -10, -5, 5, 10, 10, 10, 10, 5, -5, -5, 5, 10, 15, 15, 10,
    5, -5, -5, 5, 10, 15, 15, 10, 5, -5, -5, 5, 10, 10, 10, 10, 5, -5, -10, 0, 5, 5, 5, 5, 0, -10, -20, -10, -5, -5, -5,
    -5, -10, -20,
  )

  private val mgRookTable: Array[Int] = Array(
    0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 10, 10, 10, 10, 10, 5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0,
    0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, 0, 0, 0, 5, 5, 0, 0, 0,
  )

  private val egRookTable: Array[Int] = Array(
    0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 10, 10, 10, 10, 10, 5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0,
    0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, 0, 0, 0, 5, 5, 0, 0, 0,
  )

  private val mgQueenTable: Array[Int] = Array(
    -20, -10, -10, -5, -5, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 5, 5, 5, 0, -10, -5, 0, 5, 5, 5, 5, 0,
    -5, 0, 0, 5, 5, 5, 5, 0, -5, -10, 5, 5, 5, 5, 5, 0, -10, -10, 0, 5, 0, 0, 0, 0, -10, -20, -10, -10, -5, -5, -10,
    -10, -20,
  )

  private val egQueenTable: Array[Int] = Array(
    -15, -10, -8, -5, -5, -8, -10, -15, -10, 0, 3, 5, 5, 3, 0, -10, -8, 3, 10, 10, 10, 10, 3, -8, -5, 5, 10, 15, 15, 10,
    5, -5, -5, 5, 10, 15, 15, 10, 5, -5, -8, 3, 10, 10, 10, 10, 3, -8, -10, 0, 3, 5, 5, 3, 0, -10, -15, -10, -8, -5, -5,
    -8, -10, -15,
  )

  private val mgKingTable: Array[Int] = Array(
    -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40,
    -30, -30, -40, -40, -50, -50, -40, -40, -30, -20, -30, -30, -40, -40, -30, -30, -20, -10, -20, -20, -20, -20, -20,
    -20, -10, 20, 20, 0, 0, 0, 0, 20, 20, 20, 30, 10, 0, 0, 10, 30, 20,
  )

  private val egKingTable: Array[Int] = Array(
    -50, -40, -30, -20, -20, -30, -40, -50, -30, -20, -10, 0, 0, -10, -20, -30, -30, -10, 20, 30, 30, 20, -10, -30, -30,
    -10, 30, 40, 40, 30, -10, -30, -30, -10, 30, 40, 40, 30, -10, -30, -30, -10, 20, 30, 30, 20, -10, -30, -30, -30, 0,
    0, 0, 0, -30, -30, -50, -30, -30, -30, -30, -30, -30, -50,
  )

  private val phaseWeight: Map[PieceType, Int] = Map(
    PieceType.Knight -> 1,
    PieceType.Bishop -> 1,
    PieceType.Rook   -> 2,
    PieceType.Queen  -> 4,
  )
  private val maxPhase = 24 // 4*4 + 4*2 + 4*1 + 4*1

  private val passedPawnBonus: Array[Int]   = Array(0, 5, 10, 20, 35, 60, 100, 0)
  private val egPassedPawnBonus: Array[Int] = Array(0, 20, 40, 80, 150, 250, 400, 0)

  // Pawn structure penalties
  private val doubledMg  = -10
  private val doubledEg  = -25
  private val isolatedMg = -15
  private val isolatedEg = -20

  // Mobility weights: centipawns per reachable square (indexed by PieceType.ordinal)
  private val mobilityMg = Array(0, 4, 3, 2, 1, 0, 0)
  private val mobilityEg = Array(0, 4, 3, 4, 2, 0, 0)

  // Direction offsets for sliding pieces
  private val diagonals     = List((-1, -1), (-1, 1), (1, -1), (1, 1))
  private val orthogonals   = List((-1, 0), (1, 0), (0, -1), (0, 1))
  private val knightOffsets = List((-2, -1), (-2, 1), (-1, -2), (-1, 2), (1, -2), (1, 2), (2, -1), (2, 1))

  // Rook and bishop bonuses
  private val bishopPairMg = 50
  private val bishopPairEg = 70
  private val rookOn7thMg  = 20
  private val rookOn7thEg  = 10

  /** Evaluate the position from the perspective of context.turn. Positive = good for context.turn.
    */
  def evaluate(context: GameContext): Int =
    val phase      = gamePhase(context.board)
    val isEg       = isEndgame(phase)
    val material   = materialAndPositional(context, phase)
    val structure  = pawnStructure(context, phase)
    val mobility   = mobilityScore(context, phase)
    val rookBishop = rookAndBishopBonuses(context, phase)
    val bonuses    = positionalBonuses(context, phase, isEg)
    val egBonuses  = if isEg then endgameBonus(context) else 0
    material + structure + mobility + rookBishop + bonuses + egBonuses + TEMPO_BONUS

  private def gamePhase(board: de.nowchess.api.board.Board): Int =
    val phase = board.pieces.values.foldLeft(0) { (acc, piece) =>
      acc + phaseWeight.getOrElse(piece.pieceType, 0)
    }
    math.min(phase, maxPhase)

  private def isEndgame(phase: Int): Boolean =
    phase < 8 // Significantly reduced material indicates endgame

  private def taper(mg: Int, eg: Int, phase: Int): Int =
    (mg * phase + eg * (maxPhase - phase)) / maxPhase

  private def materialAndPositional(context: GameContext, phase: Int): Int =
    val (mg, eg) = context.board.pieces.foldLeft((0, 0)) { case ((mg, eg), (square, piece)) =>
      val (psqMg, psqEg) = squareBonus(piece.pieceType, piece.color, square)
      val pieceMg        = mgMaterial(piece.pieceType.ordinal) + psqMg
      val pieceEg        = egMaterial(piece.pieceType.ordinal) + psqEg
      val sign           = if piece.color == context.turn then 1 else -1
      (mg + sign * pieceMg, eg + sign * pieceEg)
    }
    taper(mg, eg, phase)

  private def squareBonus(pieceType: PieceType, color: Color, sq: Square): (Int, Int) =
    val rankIdx   = if color == Color.White then sq.rank.ordinal else 7 - sq.rank.ordinal
    val fileIdx   = sq.file.ordinal
    val squareIdx = rankIdx * 8 + fileIdx

    pieceType match
      case PieceType.Pawn   => (mgPawnTable(squareIdx), egPawnTable(squareIdx))
      case PieceType.Knight => (mgKnightTable(squareIdx), egKnightTable(squareIdx))
      case PieceType.Bishop => (mgBishopTable(squareIdx), egBishopTable(squareIdx))
      case PieceType.Rook   => (mgRookTable(squareIdx), egRookTable(squareIdx))
      case PieceType.Queen  => (mgQueenTable(squareIdx), egQueenTable(squareIdx))
      case PieceType.King   => (mgKingTable(squareIdx), egKingTable(squareIdx))

  private def pawnStructure(context: GameContext, phase: Int): Int =
    val friendlyPawns = context.board.pieces.filter((_, p) => p.color == context.turn && p.pieceType == PieceType.Pawn)
    val enemyPawns    = context.board.pieces.filter((_, p) => p.color != context.turn && p.pieceType == PieceType.Pawn)

    val friendlyByFile = friendlyPawns.groupMap(s => s._1.file.ordinal)(s => s._1.rank.ordinal)
    val enemyByFile    = enemyPawns.groupMap(s => s._1.file.ordinal)(s => s._1.rank.ordinal)

    val (fMg, fEg) = structureScore(friendlyByFile)
    val (eMg, eEg) = structureScore(enemyByFile)
    taper(fMg - eMg, fEg - eEg, phase)

  private def structureScore(byFile: Map[Int, Iterable[Int]]): (Int, Int) =
    byFile.foldLeft((0, 0)) { case ((mg, eg), (file, ranks)) =>
      val doubled     = (ranks.size - 1).max(0)
      val hasAdjacent = (file - 1 to file + 1).filter(f => f >= 0 && f < 8 && f != file).exists(byFile.contains)
      val isolated    = if !hasAdjacent then ranks.size else 0
      (mg + doubled * doubledMg + isolated * isolatedMg, eg + doubled * doubledEg + isolated * isolatedEg)
    }

  private def positionalBonuses(context: GameContext, phase: Int, isEg: Boolean): Int =
    context.board.pieces.foldLeft(0) { case (score, (sq, piece)) =>
      val bonus = piece.pieceType match
        case PieceType.Pawn =>
          if isPassedPawn(context.board, sq, piece.color) then
            if isEg then egPassedPawnBonus(sq.rank.ordinal) else passedPawnBonus(sq.rank.ordinal)
          else 0
        case PieceType.Rook => rookOpenFileBonus(context.board, sq, piece.color)
        case PieceType.King => kingShieldBonus(context.board, sq, piece.color, phase)
        case _              => 0
      if piece.color == context.turn then score + bonus else score - bonus
    }

  private def isPassedPawn(board: de.nowchess.api.board.Board, sq: Square, color: Color): Boolean =
    val enemyColor = color.opposite
    val pawnRank   = sq.rank.ordinal
    val fileRange  = (sq.file.ordinal - 1 to sq.file.ordinal + 1).filter(f => f >= 0 && f < 8)
    val rankCheck  = if color == Color.White then (r: Int) => r > pawnRank else (r: Int) => r < pawnRank

    board.pieces.forall { (enemySq, enemyPiece) =>
      !(enemyPiece.color == enemyColor &&
        enemyPiece.pieceType == PieceType.Pawn &&
        fileRange.contains(enemySq.file.ordinal) &&
        rankCheck(enemySq.rank.ordinal))
    }

  private def rookOpenFileBonus(board: de.nowchess.api.board.Board, rookSq: Square, color: Color): Int =
    val hasFriendlyPawn = board.pieces.exists { (sq, piece) =>
      piece.color == color && piece.pieceType == PieceType.Pawn && sq.file == rookSq.file
    }
    val hasEnemyPawn = board.pieces.exists { (sq, piece) =>
      piece.color != color && piece.pieceType == PieceType.Pawn && sq.file == rookSq.file
    }
    if !hasFriendlyPawn && !hasEnemyPawn then 20 // open file
    else if !hasFriendlyPawn then 10             // semi-open file
    else 0

  private def kingShieldBonus(board: de.nowchess.api.board.Board, kingSq: Square, color: Color, phase: Int): Int =
    val shieldRankDelta = if color == Color.White then 1 else -1
    val shieldFiles     = (kingSq.file.ordinal - 1 to kingSq.file.ordinal + 1).filter(f => f >= 0 && f < 8)
    val shieldRank      = kingSq.rank.ordinal + shieldRankDelta

    if shieldRank < 0 || shieldRank > 7 then 0
    else
      val rawBonus = board.pieces.count { (sq, piece) =>
        piece.color == color &&
        piece.pieceType == PieceType.Pawn &&
        shieldFiles.contains(sq.file.ordinal) &&
        sq.rank.ordinal == shieldRank
      } * 10
      (rawBonus * phase) / maxPhase

  private def slidingCount(
      sq: Square,
      board: de.nowchess.api.board.Board,
      color: Color,
      directions: List[(Int, Int)],
  ): Int =
    directions.foldLeft(0) { case (total, (fileDelta, rankDelta)) =>
      @scala.annotation.tailrec
      def countRay(current: Option[Square], acc: Int): Int =
        current match
          case None => acc
          case Some(target) =>
            board.pieceAt(target) match
              case Some(piece) if piece.color == color => acc
              case Some(_)                             => acc + 1
              case None                                => countRay(target.offset(fileDelta, rankDelta), acc + 1)
      total + countRay(sq.offset(fileDelta, rankDelta), 0)
    }

  private def knightCount(sq: Square, board: de.nowchess.api.board.Board, color: Color): Int =
    knightOffsets.count { case (fileDelta, rankDelta) =>
      sq.offset(fileDelta, rankDelta).forall { target =>
        board.pieceAt(target).forall(_.color != color)
      }
    }

  private def mobilityScore(context: GameContext, phase: Int): Int =
    val (mg, eg) = context.board.pieces.foldLeft((0, 0)) { case ((mg, eg), (sq, piece)) =>
      val count = piece.pieceType match
        case PieceType.Knight => knightCount(sq, context.board, piece.color)
        case PieceType.Bishop => slidingCount(sq, context.board, piece.color, diagonals)
        case PieceType.Rook   => slidingCount(sq, context.board, piece.color, orthogonals)
        case PieceType.Queen  => slidingCount(sq, context.board, piece.color, diagonals ++ orthogonals)
        case _                => 0
      val pieceMg = count * mobilityMg(piece.pieceType.ordinal)
      val pieceEg = count * mobilityEg(piece.pieceType.ordinal)
      val sign    = if piece.color == context.turn then 1 else -1
      (mg + sign * pieceMg, eg + sign * pieceEg)
    }
    taper(mg, eg, phase)

  private def rookAndBishopBonuses(context: GameContext, phase: Int): Int =
    val (baseMg, baseEg) = bishopPairBase(context)
    val (rookMg, rookEg) = rookOn7thDelta(context)
    taper(baseMg + rookMg, baseEg + rookEg, phase)

  private def bishopPairBase(context: GameContext): (Int, Int) =
    val friendlyHasPair = hasBishopPair(context, context.turn)
    val enemyHasPair    = hasBishopPair(context, context.turn.opposite)
    val mg              = pairDelta(friendlyHasPair, enemyHasPair, bishopPairMg)
    val eg              = pairDelta(friendlyHasPair, enemyHasPair, bishopPairEg)
    (mg, eg)

  private def hasBishopPair(context: GameContext, color: Color): Boolean =
    val bishopSquares = context.board.pieces.collect {
      case (sq, piece) if piece.color == color && piece.pieceType == PieceType.Bishop => sq
    }
    bishopSquares.exists(isEvenSquare) && bishopSquares.exists(sq => !isEvenSquare(sq))

  private def isEvenSquare(square: Square): Boolean =
    (square.file.ordinal + square.rank.ordinal) % 2 == 0

  private def pairDelta(friendlyHasPair: Boolean, enemyHasPair: Boolean, bonus: Int): Int =
    (if friendlyHasPair then bonus else 0) - (if enemyHasPair then bonus else 0)

  private def rookOn7thDelta(context: GameContext): (Int, Int) =
    context.board.pieces.foldLeft((0, 0)) { case ((mg, eg), (sq, piece)) =>
      rookOn7thContribution(piece, sq, context.turn).fold((mg, eg)) { case (dMg, dEg) =>
        (mg + dMg, eg + dEg)
      }
    }

  private def rookOn7thContribution(piece: de.nowchess.api.board.Piece, sq: Square, turn: Color): Option[(Int, Int)] =
    Option.when(piece.pieceType == PieceType.Rook && isRookOn7th(piece.color, sq)) {
      val sign = if piece.color == turn then 1 else -1
      (sign * rookOn7thMg, sign * rookOn7thEg)
    }

  private def isRookOn7th(color: Color, sq: Square): Boolean =
    if color == Color.White then sq.rank.ordinal == 6 else sq.rank.ordinal == 1

  private def endgameBonus(context: GameContext): Int =
    val friendlyKing = context.board.pieces.find((_, p) => p.color == context.turn && p.pieceType == PieceType.King)
    val enemyKing    = context.board.pieces.find((_, p) => p.color != context.turn && p.pieceType == PieceType.King)

    val kingCentralBonus =
      friendlyKing.fold(0)((kSq, _) => (8 - kingCentralizationDistance(kSq)) * 15) -
        enemyKing.fold(0)((kSq, _) => (8 - kingCentralizationDistance(kSq)) * 15)

    val friendlyMaterial = materialCount(context, context.turn)
    val enemyMaterial    = materialCount(context, context.turn.opposite)
    val edgeBonus =
      if friendlyMaterial > enemyMaterial then enemyKing.fold(0)((kSq, _) => (7 - kingEdgeDistance(kSq)) * 10)
      else 0

    kingCentralBonus + edgeBonus

  private def kingCentralizationDistance(sq: Square): Int =
    val fileFromCenter = (sq.file.ordinal - 3.5).abs.toInt
    val rankFromCenter = (sq.rank.ordinal - 3.5).abs.toInt
    math.max(fileFromCenter, rankFromCenter)

  private def kingEdgeDistance(sq: Square): Int =
    val fileFromEdge = math.min(sq.file.ordinal, 7 - sq.file.ordinal)
    val rankFromEdge = math.min(sq.rank.ordinal, 7 - sq.rank.ordinal)
    math.min(fileFromEdge, rankFromEdge)

  private def materialCount(context: GameContext, color: Color): Int =
    context.board.pieces.foldLeft(0) { case (sum, (_, piece)) =>
      if piece.color == color then
        sum + (piece.pieceType match
          case PieceType.Knight => 300
          case PieceType.Bishop => 300
          case PieceType.Rook   => 500
          case PieceType.Queen  => 900
          case PieceType.Pawn   => 0
          case PieceType.King   => 0
        )
      else sum
    }
