package de.nowchess.chess.engine

import de.nowchess.api.board.{Board, Color, Piece, PieceType, Square}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.api.game.{
  ClockState,
  CorrespondenceClockState,
  DrawReason,
  GameContext,
  GameResult,
  LiveClockState,
  TimeControl,
  WinReason,
}
import de.nowchess.chess.controller.Parser
import de.nowchess.chess.observer.*
import de.nowchess.api.error.GameError
import de.nowchess.api.game.WinReason.{Checkmate, Resignation}
import de.nowchess.api.io.{GameContextExport, GameContextImport}
import de.nowchess.api.rules.RuleSet

import java.time.Instant
import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledFuture, TimeUnit}

/** Pure game engine that manages game state and notifies observers of state changes. All rule queries delegate to the
  * injected RuleSet. All user interactions go through Commands; state changes are broadcast via GameEvents.
  */
class GameEngine(
    val initialContext: GameContext = GameContext.initial,
    val ruleSet: RuleSet,
    val timeControl: TimeControl = TimeControl.Unlimited,
    initialClockState: Option[ClockState] = None,
    initialDrawOffer: Option[Color] = None,
    initialRedoStack: List[Move] = Nil,
    initialTakebackRequest: Option[Color] = None,
) extends Observable:
  // Ensure that initialBoard is set correctly for threefold repetition detection
  private val contextWithInitialBoard =
    if initialContext.moves.isEmpty && initialContext.board != initialContext.initialBoard then
      initialContext.copy(initialBoard = initialContext.board)
    else initialContext
  @SuppressWarnings(Array("DisableSyntax.var"))
  private var currentContext: GameContext = contextWithInitialBoard
  @SuppressWarnings(Array("DisableSyntax.var"))
  private var pendingDrawOffer: Option[Color] = initialDrawOffer
  @SuppressWarnings(Array("DisableSyntax.var"))
  private var clockState: Option[ClockState] =
    initialClockState.orElse(ClockState.fromTimeControl(timeControl, contextWithInitialBoard.turn, Instant.now()))
  @SuppressWarnings(Array("DisableSyntax.var"))
  private var scheduledCheck: Option[ScheduledFuture[?]] = None
  // One shared scheduler per engine; shut down with the game.
  private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  @SuppressWarnings(Array("DisableSyntax.var"))
  private var redoStack: List[Move] = initialRedoStack
  @SuppressWarnings(Array("DisableSyntax.var"))
  private var isRedoing: Boolean = false
  @SuppressWarnings(Array("DisableSyntax.var"))
  private var pendingTakebackRequest: Option[Color] = initialTakebackRequest

  // Start scheduler immediately for live clocks so passive expiry fires without waiting for a move.
  clockState.foreach(scheduleExpiryCheck)

  // Synchronized accessors for current state
  def board: Board                          = synchronized(currentContext.board)
  def turn: Color                           = synchronized(currentContext.turn)
  def context: GameContext                  = synchronized(currentContext)
  def pendingDrawOfferBy: Option[Color]     = synchronized(pendingDrawOffer)
  def currentClockState: Option[ClockState] = synchronized(clockState)

  /** Check if undo is available. */
  def canUndo: Boolean = synchronized(currentContext.moves.nonEmpty)

  /** Check if redo is available. */
  def canRedo: Boolean = synchronized(redoStack.nonEmpty)

  /** Get redo stack moves for inspection. */
  def redoStackMoves: List[Move] = synchronized(redoStack)

  /** Get pending takeback request (if any). */
  def pendingTakebackRequestBy: Option[Color] = synchronized(pendingTakebackRequest)

  /** Process a raw move input string and update game state if valid. Notifies all observers of the outcome via
    * GameEvent.
    */
  def processUserInput(rawInput: String): Unit = synchronized {
    val trimmed = rawInput.trim.toLowerCase
    trimmed match
      case "quit" | "q" =>
        ()

      case "undo" =>
        performUndo()

      case "redo" =>
        performRedo()

      case "draw" =>
        claimDraw()

      case "" =>
        notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.EmptyInput))

      case moveInput =>
        Parser.parseMove(moveInput) match
          case None =>
            notifyObservers(
              InvalidMoveEvent(
                currentContext,
                InvalidMoveReason.InvalidMoveFormat,
              ),
            )
          case Some((from, to, promotionPiece: Option[PromotionPiece])) =>
            handleParsedMove(from, to, promotionPiece)
  }

  private def handleParsedMove(from: Square, to: Square, promotionPiece: Option[PromotionPiece]): Unit =
    currentContext.board.pieceAt(from) match
      case None =>
        notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.NoSourcePiece))
      case Some(piece) if piece.color != currentContext.turn =>
        notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.NotYourPiece))
      case Some(piece) =>
        val legal = ruleSet.legalMoves(currentContext)(from)
        // Find all legal moves going to `to`
        val candidates = legal.filter(_.to == to)
        candidates match
          case Nil =>
            notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.IllegalMove))
          case _ if isPromotionMove(piece, to) =>
            if promotionPiece.isEmpty then
              notifyObservers(
                InvalidMoveEvent(currentContext, InvalidMoveReason.PromotionPieceRequired),
              )
            else
              candidates.find(_.moveType == MoveType.Promotion(promotionPiece.get)) match
                case None =>
                  notifyObservers(
                    InvalidMoveEvent(currentContext, InvalidMoveReason.PromotionPieceInvalid),
                  )
                case Some(move) => executeMove(move)
          case move :: _ =>
            executeMove(move)

  private def isPromotionMove(piece: Piece, to: Square): Boolean =
    piece.pieceType == PieceType.Pawn && {
      val promoRank = if piece.color == Color.White then 7 else 0
      to.rank.ordinal == promoRank
    }

  /** Undo the last move. */
  def undo(): Unit = synchronized(performUndo())

  /** Redo the last undone move. */
  def redo(): Unit = synchronized(performRedo())

  /** Resign from the game. The opponent wins. */
  def resign(): Unit = synchronized(resign(currentContext.turn))

  def resign(color: Color): Unit = synchronized {
    if currentContext.result.isDefined then
      notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.GameAlreadyOver))
    else
      currentContext = currentContext.withResult(Some(GameResult.Win(color.opposite, Resignation)))
      pendingDrawOffer = None
      pendingTakebackRequest = None
      stopClock()
      redoStack = Nil
      notifyObservers(ResignEvent(currentContext, color))
  }

  /** Offer a draw. */
  def offerDraw(color: Color): Unit = synchronized {
    if currentContext.result.isDefined then
      notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.GameAlreadyOver))
    else
      pendingDrawOffer match
        case Some(_) =>
          notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.DrawOfferPending))
        case None =>
          pendingDrawOffer = Some(color)
          notifyObservers(DrawOfferEvent(currentContext, color))
  }

  /** Accept a pending draw offer. */
  def acceptDraw(color: Color): Unit = synchronized {
    if currentContext.result.isDefined then
      notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.GameAlreadyOver))
    else
      pendingDrawOffer match
        case None =>
          notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.NoDrawOfferToAccept))
        case Some(offerer) if offerer == color =>
          notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.CannotAcceptOwnDrawOffer))
        case Some(_) =>
          currentContext = currentContext.withResult(Some(GameResult.Draw(DrawReason.Agreement)))
          pendingDrawOffer = None
          pendingTakebackRequest = None
          stopClock()
          redoStack = Nil
          notifyObservers(DrawEvent(currentContext, DrawReason.Agreement))
  }

  /** Decline a pending draw offer. */
  def declineDraw(color: Color): Unit = synchronized {
    if currentContext.result.isDefined then
      notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.GameAlreadyOver))
    else
      pendingDrawOffer match
        case None =>
          notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.NoDrawOfferToDecline))
        case Some(offerer) if offerer == color =>
          notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.CannotDeclineOwnDrawOffer))
        case Some(_) =>
          pendingDrawOffer = None
          notifyObservers(DrawOfferDeclinedEvent(currentContext, color))
  }

  /** Claim a draw by fifty-move rule or threefold repetition. */
  def claimDraw(): Unit = synchronized {
    if currentContext.result.isDefined then
      notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.GameAlreadyOver))
    else if currentContext.halfMoveClock >= 100 then
      currentContext = currentContext.withResult(Some(GameResult.Draw(DrawReason.FiftyMoveRule)))
      stopClock()
      redoStack = Nil
      notifyObservers(DrawEvent(currentContext, DrawReason.FiftyMoveRule))
    else if ruleSet.isThreefoldRepetition(currentContext) then
      currentContext = currentContext.withResult(Some(GameResult.Draw(DrawReason.ThreefoldRepetition)))
      stopClock()
      redoStack = Nil
      notifyObservers(DrawEvent(currentContext, DrawReason.ThreefoldRepetition))
    else notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.DrawCannotBeClaimed))
  }

  /** Load a game using the provided importer. If the imported context has moves, they are replayed through the command
    * system. Otherwise, the position is set directly. Notifies observers with PgnLoadedEvent on success.
    */
  def loadGame(importer: GameContextImport, input: String): Either[GameError, Unit] = synchronized {
    importer.importGameContext(input) match
      case Left(err) => Left(err)
      case Right(ctx) =>
        replayGame(ctx).map { _ =>
          pendingDrawOffer = None
          pendingTakebackRequest = None
          redoStack = Nil
          stopClock()
          clockState = ClockState.fromTimeControl(timeControl, currentContext.turn, Instant.now())
          notifyObservers(PgnLoadedEvent(currentContext))
        }
  }

  private def replayGame(ctx: GameContext): Either[GameError, Unit] =
    val savedContext = currentContext
    currentContext = GameContext.initial
    redoStack = Nil

    if ctx.moves.isEmpty then
      currentContext = ctx.copy(initialBoard = ctx.board)
      Right(())
    else replayMoves(ctx.moves, savedContext)

  private[engine] def replayMoves(moves: List[Move], savedContext: GameContext): Either[GameError, Unit] =
    val result = moves.foldLeft[Either[GameError, Unit]](Right(())) { (acc, move) =>
      acc.flatMap(_ => applyReplayMove(move))
    }
    result.left.foreach(_ => currentContext = savedContext)
    result

  private def applyReplayMove(move: Move): Either[GameError, Unit] =
    val legal = ruleSet.legalMoves(currentContext)(move.from)
    val candidate = move.moveType match
      case MoveType.Promotion(pp) => legal.find(m => m.to == move.to && m.moveType == MoveType.Promotion(pp))
      case _                      => legal.find(_.to == move.to)
    candidate match
      case None     => Left(GameError.IllegalMove)
      case Some(lm) => executeMove(lm); Right(())

  /** Export the current game context using the provided exporter. */
  def exportGame(exporter: GameContextExport): String = synchronized {
    exporter.exportGameContext(currentContext)
  }

  /** Load an arbitrary board position, clearing all history and undo/redo state. */
  def loadPosition(newContext: GameContext): Unit = synchronized {
    val contextWithInitialBoard =
      if newContext.moves.isEmpty then newContext.copy(initialBoard = newContext.board)
      else newContext
    currentContext = contextWithInitialBoard
    pendingDrawOffer = None
    pendingTakebackRequest = None
    redoStack = Nil
    stopClock()
    clockState = ClockState.fromTimeControl(timeControl, currentContext.turn, Instant.now())
    notifyObservers(BoardResetEvent(currentContext))
  }

  /** Reset the board to initial position. */
  def reset(): Unit = synchronized {
    currentContext = GameContext.initial
    pendingDrawOffer = None
    pendingTakebackRequest = None
    redoStack = Nil
    stopClock()
    clockState = ClockState.fromTimeControl(timeControl, currentContext.turn, Instant.now())
    notifyObservers(BoardResetEvent(currentContext))
  }

  /** Apply a draw result directly (for agreement, fifty-move claim, etc.). */
  def applyDraw(reason: DrawReason): Unit = synchronized {
    if currentContext.result.isEmpty then
      currentContext = currentContext.withResult(Some(GameResult.Draw(reason)))
      stopClock()
      redoStack = Nil
      notifyObservers(DrawEvent(currentContext, reason))
  }

  /** Inject clock state directly (for testing). */
  private[engine] def injectClockState(cs: Option[ClockState]): Unit = synchronized { clockState = cs }

  // ──── Clock helpers ────

  private def advanceClock(movedColor: Color): Unit =
    clockState.foreach { cs =>
      cs.afterMove(movedColor, Instant.now()) match
        case Left(flagged)  => clockState = None; cancelScheduled(); handleTimeFlag(flagged)
        case Right(updated) => clockState = Some(updated); scheduleExpiryCheck(updated)
    }

  private def handleTimeFlag(flagged: Color): Unit =
    val result =
      if ruleSet.isInsufficientMaterial(currentContext) then GameResult.Draw(DrawReason.InsufficientMaterial)
      else GameResult.Win(flagged.opposite, WinReason.TimeControl)
    currentContext = currentContext.withResult(Some(result))
    pendingDrawOffer = None
    pendingTakebackRequest = None
    redoStack = Nil
    notifyObservers(TimeFlagEvent(currentContext, flagged))

  private def scheduleExpiryCheck(cs: ClockState): Unit =
    cancelScheduled()
    cs match
      case live: LiveClockState =>
        val delayMs = math.max(0L, live.remainingMs(live.activeColor, Instant.now()))
        val future = scheduler.schedule(
          new Runnable { def run(): Unit = checkClockExpiry() },
          delayMs,
          TimeUnit.MILLISECONDS,
        )
        scheduledCheck = Some(future)
      case _ => ()

  private def cancelScheduled(): Unit =
    scheduledCheck.foreach(_.cancel(false))
    scheduledCheck = None

  private def stopClock(): Unit =
    cancelScheduled()
    clockState = None

  private def checkClockExpiry(): Unit = synchronized {
    if currentContext.result.isEmpty then
      clockState.foreach { cs =>
        if cs.remainingMs(cs.activeColor, Instant.now()) <= 0 then
          clockState = None
          handleTimeFlag(cs.activeColor)
      }
  }

  // ──── Private helpers ────

  private def executeMove(move: Move): Unit =
    if !isRedoing then
      redoStack = Nil
      pendingTakebackRequest = None

    val contextBefore = currentContext
    val nextContext   = ruleSet.applyMove(currentContext)(move)
    val captured      = computeCaptured(currentContext, move)
    val notation      = translateMoveToNotation(move, contextBefore.board)
    currentContext = nextContext

    advanceClock(contextBefore.turn)

    notifyObservers(
      MoveExecutedEvent(
        currentContext,
        move.from.toString,
        move.to.toString,
        captured.map(c => s"${c.color.label} ${c.pieceType.label}"),
      ),
    )

    val status = ruleSet.postMoveStatus(currentContext)
    if currentContext.result.isEmpty then
      if status.isCheckmate then
        val winner = currentContext.turn.opposite
        currentContext = currentContext.withResult(Some(GameResult.Win(winner, Checkmate)))
        cancelScheduled()
        notifyObservers(CheckmateEvent(currentContext, winner))
        redoStack = Nil
      else if status.isStalemate then
        currentContext = currentContext.withResult(Some(GameResult.Draw(DrawReason.Stalemate)))
        cancelScheduled()
        notifyObservers(DrawEvent(currentContext, DrawReason.Stalemate))
        redoStack = Nil
      else if status.isInsufficientMaterial then
        currentContext = currentContext.withResult(Some(GameResult.Draw(DrawReason.InsufficientMaterial)))
        cancelScheduled()
        notifyObservers(DrawEvent(currentContext, DrawReason.InsufficientMaterial))
        redoStack = Nil
      else if status.isCheck then notifyObservers(CheckDetectedEvent(currentContext))

    if currentContext.halfMoveClock >= 100 then notifyObservers(FiftyMoveRuleAvailableEvent(currentContext))
    if status.isThreefoldRepetition then notifyObservers(ThreefoldRepetitionAvailableEvent(currentContext))

  private def translateMoveToNotation(move: Move, boardBefore: Board): String =
    move.moveType match
      case MoveType.CastleKingside    => "O-O"
      case MoveType.CastleQueenside   => "O-O-O"
      case MoveType.EnPassant         => enPassantNotation(move)
      case MoveType.Promotion(pp)     => promotionNotation(move, pp)
      case MoveType.Normal(isCapture) => normalMoveNotation(move, boardBefore, isCapture)

  private def enPassantNotation(move: Move): String =
    s"${move.from.file.toString.toLowerCase}x${move.to}"

  private def promotionNotation(move: Move, piece: PromotionPiece): String =
    val ppChar = piece match
      case PromotionPiece.Queen  => "Q"
      case PromotionPiece.Rook   => "R"
      case PromotionPiece.Bishop => "B"
      case PromotionPiece.Knight => "N"
    s"${move.to}=$ppChar"

  private[engine] def normalMoveNotation(move: Move, boardBefore: Board, isCapture: Boolean): String =
    boardBefore.pieceAt(move.from).map(_.pieceType) match
      case Some(PieceType.Pawn) =>
        if isCapture then s"${move.from.file.toString.toLowerCase}x${move.to}"
        else move.to.toString
      case Some(pt) =>
        val letter = pieceNotation(pt)
        if isCapture then s"${letter}x${move.to}" else s"$letter${move.to}"
      case None => move.to.toString

  private[engine] def pieceNotation(pieceType: PieceType): String =
    pieceType match
      case PieceType.Knight => "N"
      case PieceType.Bishop => "B"
      case PieceType.Rook   => "R"
      case PieceType.Queen  => "Q"
      case PieceType.King   => "K"
      case _                => ""

  private def computeCaptured(context: GameContext, move: Move): Option[Piece] =
    move.moveType match
      case MoveType.EnPassant =>
        // Captured pawn is on the same rank as the moving pawn, same file as destination
        val capturedSquare = Square(move.to.file, move.from.rank)
        context.board.pieceAt(capturedSquare)
      case MoveType.CastleKingside | MoveType.CastleQueenside =>
        None
      case _ =>
        context.board.pieceAt(move.to)

  private def replayContextFromMoves(moves: List[Move]): GameContext =
    moves.foldLeft(contextWithInitialBoard)((ctx, move) => ruleSet.applyMove(ctx)(move))

  private def performUndo(): Unit =
    if currentContext.moves.isEmpty then
      notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.NothingToUndo))
    else
      val lastMove = currentContext.moves.last
      val prevCtx  = replayContextFromMoves(currentContext.moves.dropRight(1))
      val notation = translateMoveToNotation(lastMove, prevCtx.board)
      redoStack = lastMove :: redoStack
      currentContext = prevCtx
      notifyObservers(MoveUndoneEvent(currentContext, notation))

  private def performRedo(): Unit =
    if redoStack.isEmpty then notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.NothingToRedo))
    else
      val move = redoStack.head
      redoStack = redoStack.tail
      isRedoing = true
      executeMove(move)
      isRedoing = false

  def requestTakeback(color: Color): Unit = synchronized {
    if currentContext.result.isDefined then
      notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.GameAlreadyOver))
    else if currentContext.moves.isEmpty then
      notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.NothingToUndo))
    else
      pendingTakebackRequest match
        case Some(_) =>
          notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.TakebackRequestPending))
        case None =>
          pendingTakebackRequest = Some(color)
          notifyObservers(TakebackRequestedEvent(currentContext, color))
  }

  def acceptTakeback(color: Color): Unit = synchronized {
    if currentContext.result.isDefined then
      notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.GameAlreadyOver))
    else
      pendingTakebackRequest match
        case None =>
          notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.NoTakebackRequestToAccept))
        case Some(requester) if requester == color =>
          notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.CannotAcceptOwnTakebackRequest))
        case Some(_) =>
          pendingTakebackRequest = None
          performUndo()
  }

  def declineTakeback(color: Color): Unit = synchronized {
    if currentContext.result.isDefined then
      notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.GameAlreadyOver))
    else
      pendingTakebackRequest match
        case None =>
          notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.NoTakebackRequestToDecline))
        case Some(requester) if requester == color =>
          notifyObservers(InvalidMoveEvent(currentContext, InvalidMoveReason.CannotDeclineOwnTakebackRequest))
        case Some(_) =>
          pendingTakebackRequest = None
          notifyObservers(TakebackDeclinedEvent(currentContext, color))
  }
