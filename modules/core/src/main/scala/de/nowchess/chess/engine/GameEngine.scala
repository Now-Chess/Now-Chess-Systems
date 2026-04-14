package de.nowchess.chess.engine

import de.nowchess.api.board.{Board, Color, Piece, PieceType, Square}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.api.game.{DrawReason, GameContext, GameResult}
import de.nowchess.chess.controller.Parser
import de.nowchess.chess.observer.*
import de.nowchess.chess.command.{CommandInvoker, MoveCommand, MoveResult}
import de.nowchess.io.{GameContextExport, GameContextImport}
import de.nowchess.rules.RuleSet
import de.nowchess.rules.sets.DefaultRules

/** Pure game engine that manages game state and notifies observers of state changes. All rule queries delegate to the
  * injected RuleSet. All user interactions go through Commands; state changes are broadcast via GameEvents.
  */
class GameEngine(
    val initialContext: GameContext = GameContext.initial,
    val ruleSet: RuleSet = DefaultRules,
) extends Observable:
  @SuppressWarnings(Array("DisableSyntax.var"))
  private var currentContext: GameContext = initialContext
  private val invoker                     = new CommandInvoker()

  /** Pending promotion: the Move that triggered it (from/to only, moveType filled in later). */
  private case class PendingPromotion(from: Square, to: Square, contextBefore: GameContext)
  @SuppressWarnings(Array("DisableSyntax.var"))
  private var pendingPromotion: Option[PendingPromotion] = None

  /** True if a pawn promotion move is pending and needs a piece choice. */
  def isPendingPromotion: Boolean = synchronized(pendingPromotion.isDefined)

  // Synchronized accessors for current state
  def board: Board         = synchronized(currentContext.board)
  def turn: Color          = synchronized(currentContext.turn)
  def context: GameContext = synchronized(currentContext)

  /** Check if undo is available. */
  def canUndo: Boolean = synchronized(invoker.canUndo)

  /** Check if redo is available. */
  def canRedo: Boolean = synchronized(invoker.canRedo)

  /** Get the command history for inspection (testing/debugging). */
  def commandHistory: List[de.nowchess.chess.command.Command] = synchronized(invoker.history)

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
        if currentContext.halfMoveClock >= 100 then
          currentContext = currentContext.withResult(Some(GameResult.Draw(DrawReason.FiftyMoveRule)))
          invoker.clear()
          notifyObservers(DrawEvent(currentContext, DrawReason.FiftyMoveRule))
        else
          notifyObservers(
            InvalidMoveEvent(
              currentContext,
              "Draw cannot be claimed: the 50-move rule has not been triggered.",
            ),
          )

      case "" =>
        notifyObservers(InvalidMoveEvent(currentContext, "Please enter a valid move or command."))

      case moveInput =>
        Parser.parseMove(moveInput) match
          case None =>
            notifyObservers(
              InvalidMoveEvent(
                currentContext,
                s"Invalid move format '$moveInput'. Use coordinate notation, e.g. e2e4.",
              ),
            )
          case Some((from, to)) =>
            handleParsedMove(from, to)
  }

  private def handleParsedMove(from: Square, to: Square): Unit =
    currentContext.board.pieceAt(from) match
      case None =>
        notifyObservers(InvalidMoveEvent(currentContext, "No piece on that square."))
      case Some(piece) if piece.color != currentContext.turn =>
        notifyObservers(InvalidMoveEvent(currentContext, "That is not your piece."))
      case Some(piece) =>
        val legal = ruleSet.legalMoves(currentContext)(from)
        // Find all legal moves going to `to`
        val candidates = legal.filter(_.to == to)
        candidates match
          case Nil =>
            notifyObservers(InvalidMoveEvent(currentContext, "Illegal move."))
          case moves if isPromotionMove(piece, to) =>
            // Multiple moves (one per promotion piece) — ask user to choose
            val contextBefore = currentContext
            pendingPromotion = Some(PendingPromotion(from, to, contextBefore))
            notifyObservers(PromotionRequiredEvent(currentContext, from, to))
          case move :: _ =>
            executeMove(move)

  private def isPromotionMove(piece: Piece, to: Square): Boolean =
    piece.pieceType == PieceType.Pawn && {
      val promoRank = if piece.color == Color.White then 7 else 0
      to.rank.ordinal == promoRank
    }

  /** Apply a player's promotion piece choice. Must only be called when isPendingPromotion is true.
    */
  def completePromotion(piece: PromotionPiece): Unit = synchronized {
    pendingPromotion match
      case None =>
        notifyObservers(InvalidMoveEvent(currentContext, "No promotion pending."))
      case Some(pending) =>
        pendingPromotion = None
        val move = Move(pending.from, pending.to, MoveType.Promotion(piece))
        // Verify it's actually legal
        val legal = ruleSet.legalMoves(currentContext)(pending.from)
        if legal.contains(move) then executeMove(move)
        else notifyObservers(InvalidMoveEvent(currentContext, "Error completing promotion."))
  }

  /** Undo the last move. */
  def undo(): Unit = synchronized(performUndo())

  /** Redo the last undone move. */
  def redo(): Unit = synchronized(performRedo())

  /** Load a game using the provided importer. If the imported context has moves, they are replayed through the command
    * system. Otherwise, the position is set directly. Notifies observers with PgnLoadedEvent on success.
    */
  def loadGame(importer: GameContextImport, input: String): Either[String, Unit] = synchronized {
    importer.importGameContext(input) match
      case Left(err) => Left(err)
      case Right(ctx) =>
        replayGame(ctx).map { _ =>
          notifyObservers(PgnLoadedEvent(currentContext))
        }
  }

  private def replayGame(ctx: GameContext): Either[String, Unit] =
    val savedContext = currentContext
    currentContext = GameContext.initial
    pendingPromotion = None
    invoker.clear()

    if ctx.moves.isEmpty then
      currentContext = ctx
      Right(())
    else replayMoves(ctx.moves, savedContext)

  private[engine] def replayMoves(moves: List[Move], savedContext: GameContext): Either[String, Unit] =
    val result = moves.foldLeft[Either[String, Unit]](Right(())) { (acc, move) =>
      acc.flatMap(_ => applyReplayMove(move))
    }
    result.left.foreach(_ => currentContext = savedContext)
    result

  private def applyReplayMove(move: Move): Either[String, Unit] =
    handleParsedMove(move.from, move.to)
    move.moveType match
      case MoveType.Promotion(pp) if pendingPromotion.isDefined =>
        completePromotion(pp)
        Right(())
      case MoveType.Promotion(_) =>
        Left(s"Promotion required for move ${move.from}${move.to}")
      case _ => Right(())

  /** Export the current game context using the provided exporter. */
  def exportGame(exporter: GameContextExport): String = synchronized {
    exporter.exportGameContext(currentContext)
  }

  /** Load an arbitrary board position, clearing all history and undo/redo state. */
  def loadPosition(newContext: GameContext): Unit = synchronized {
    currentContext = newContext
    pendingPromotion = None
    invoker.clear()
    notifyObservers(BoardResetEvent(currentContext))
  }

  /** Reset the board to initial position. */
  def reset(): Unit = synchronized {
    currentContext = GameContext.initial
    invoker.clear()
    notifyObservers(BoardResetEvent(currentContext))
  }

  // ──── Private helpers ────

  private def executeMove(move: Move): Unit =
    val contextBefore = currentContext
    val nextContext   = ruleSet.applyMove(currentContext)(move)
    val captured      = computeCaptured(currentContext, move)

    val cmd = MoveCommand(
      from = move.from,
      to = move.to,
      moveResult = Some(MoveResult.Successful(nextContext, captured)),
      previousContext = Some(contextBefore),
      notation = translateMoveToNotation(move, contextBefore.board),
    )
    invoker.execute(cmd)
    currentContext = nextContext

    notifyObservers(
      MoveExecutedEvent(
        currentContext,
        move.from.toString,
        move.to.toString,
        captured.map(c => s"${c.color.label} ${c.pieceType.label}"),
      ),
    )

    if ruleSet.isCheckmate(currentContext) then
      val winner = currentContext.turn.opposite
      currentContext = currentContext.withResult(Some(GameResult.Win(winner)))
      notifyObservers(CheckmateEvent(currentContext, winner))
      invoker.clear()
    else if ruleSet.isStalemate(currentContext) then
      currentContext = currentContext.withResult(Some(GameResult.Draw(DrawReason.Stalemate)))
      notifyObservers(DrawEvent(currentContext, DrawReason.Stalemate))
      invoker.clear()
    else if ruleSet.isInsufficientMaterial(currentContext) then
      currentContext = currentContext.withResult(Some(GameResult.Draw(DrawReason.InsufficientMaterial)))
      notifyObservers(DrawEvent(currentContext, DrawReason.InsufficientMaterial))
      invoker.clear()
    else if ruleSet.isCheck(currentContext) then notifyObservers(CheckDetectedEvent(currentContext))

    if currentContext.halfMoveClock >= 100 then notifyObservers(FiftyMoveRuleAvailableEvent(currentContext))

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

  private def performUndo(): Unit =
    if invoker.canUndo then
      val cmd = invoker.history(invoker.getCurrentIndex)
      (cmd: @unchecked) match
        case moveCmd: MoveCommand =>
          moveCmd.previousContext.foreach(currentContext = _)
          invoker.undo()
          notifyObservers(MoveUndoneEvent(currentContext, moveCmd.notation))
    else notifyObservers(InvalidMoveEvent(currentContext, "Nothing to undo."))

  private def performRedo(): Unit =
    if invoker.canRedo then
      val cmd = invoker.history(invoker.getCurrentIndex + 1)
      (cmd: @unchecked) match
        case moveCmd: MoveCommand =>
          for case MoveResult.Successful(nextCtx, cap) <- moveCmd.moveResult do
            currentContext = nextCtx
            invoker.redo()
            val capturedDesc = cap.map(c => s"${c.color.label} ${c.pieceType.label}")
            notifyObservers(
              MoveRedoneEvent(
                currentContext,
                moveCmd.notation,
                moveCmd.from.toString,
                moveCmd.to.toString,
                capturedDesc,
              ),
            )
    else notifyObservers(InvalidMoveEvent(currentContext, "Nothing to redo."))
