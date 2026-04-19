package de.nowchess.chess.engine

import de.nowchess.api.board.{Board, Color, Piece, PieceType, Square}
import de.nowchess.api.move.{Move, MoveType, PromotionPiece}
import de.nowchess.api.game.{BotParticipant, DrawReason, GameContext, GameResult, Human, Participant}
import de.nowchess.api.player.{PlayerId, PlayerInfo}
import de.nowchess.chess.controller.Parser
import de.nowchess.chess.observer.*
import de.nowchess.chess.command.{CommandInvoker, MoveCommand, MoveResult}
import de.nowchess.io.{GameContextExport, GameContextImport}
import de.nowchess.rules.RuleSet
import de.nowchess.rules.sets.DefaultRules

import scala.concurrent.{ExecutionContext, Future}

/** Pure game engine that manages game state and notifies observers of state changes. All rule queries delegate to the
  * injected RuleSet. All user interactions go through Commands; state changes are broadcast via GameEvents.
  */
class GameEngine(
    val initialContext: GameContext = GameContext.initial,
    val ruleSet: RuleSet = DefaultRules,
    val participants: Map[Color, Participant] = Map(
      Color.White -> Human(PlayerInfo(PlayerId("p1"), "Player 1")),
      Color.Black -> Human(PlayerInfo(PlayerId("p2"), "Player 2")),
    ),
) extends Observable:
  // Ensure that initialBoard is set correctly for threefold repetition detection
  private val contextWithInitialBoard =
    if initialContext.moves.isEmpty && initialContext.board != initialContext.initialBoard then
      initialContext.copy(initialBoard = initialContext.board)
    else initialContext
  @SuppressWarnings(Array("DisableSyntax.var"))
  private var currentContext: GameContext = contextWithInitialBoard
  private val invoker                     = new CommandInvoker()

  private implicit val ec: ExecutionContext = ExecutionContext.global

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
        else if ruleSet.isThreefoldRepetition(currentContext) then
          currentContext = currentContext.withResult(Some(GameResult.Draw(DrawReason.ThreefoldRepetition)))
          invoker.clear()
          notifyObservers(DrawEvent(currentContext, DrawReason.ThreefoldRepetition))
        else
          notifyObservers(
            InvalidMoveEvent(
              currentContext,
              "Draw cannot be claimed: neither the 50-move rule nor threefold repetition has been triggered.",
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
          case Some((from, to, promotionPiece: Option[PromotionPiece])) =>
            handleParsedMove(from, to, promotionPiece)
  }

  private def handleParsedMove(from: Square, to: Square, promotionPiece: Option[PromotionPiece]): Unit =
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
          case _ if isPromotionMove(piece, to) =>
            if promotionPiece.isEmpty then
              notifyObservers(
                InvalidMoveEvent(currentContext, "Promotion piece required: append q, r, b, or n to the move."),
              )
            else
              candidates.find(_.moveType == MoveType.Promotion(promotionPiece.get)) match
                case None =>
                  notifyObservers(
                    InvalidMoveEvent(currentContext, "Error completing promotion: no matching legal move."),
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
    invoker.clear()

    if ctx.moves.isEmpty then
      currentContext = ctx.copy(initialBoard = ctx.board)
      Right(())
    else replayMoves(ctx.moves, savedContext)

  private[engine] def replayMoves(moves: List[Move], savedContext: GameContext): Either[String, Unit] =
    val result = moves.foldLeft[Either[String, Unit]](Right(())) { (acc, move) =>
      acc.flatMap(_ => applyReplayMove(move))
    }
    result.left.foreach(_ => currentContext = savedContext)
    result

  private def applyReplayMove(move: Move): Either[String, Unit] =
    val legal = ruleSet.legalMoves(currentContext)(move.from)
    val candidate = move.moveType match
      case MoveType.Promotion(pp) => legal.find(m => m.to == move.to && m.moveType == MoveType.Promotion(pp))
      case _                      => legal.find(_.to == move.to)
    candidate match
      case None     => Left("Illegal move.")
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
    invoker.clear()
    notifyObservers(BoardResetEvent(currentContext))
  }

  /** Reset the board to initial position. */
  def reset(): Unit = synchronized {
    currentContext = GameContext.initial
    invoker.clear()
    notifyObservers(BoardResetEvent(currentContext))
  }

  /** Kick off play when the side to move is a bot (e.g. bot-vs-bot from initial position). */
  def startGame(): Unit = synchronized(requestBotMoveIfNeeded())

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
    if ruleSet.isThreefoldRepetition(currentContext) then
      notifyObservers(ThreefoldRepetitionAvailableEvent(currentContext))
    else requestBotMoveIfNeeded()

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

  /** Request a move from the opponent bot if it's their turn. Spawns an async task to avoid blocking the engine.
    */
  private def requestBotMoveIfNeeded(): Unit =
    val pendingBotMove = synchronized {
      participants.get(currentContext.turn) match
        case Some(BotParticipant(bot)) => Some((bot, currentContext))
        case _                         => None
    }

    pendingBotMove.foreach { case (bot, contextAtRequest) =>
      Future {
        bot.nextMove(contextAtRequest) match
          case Some(move) => applyBotMove(move)
          case None       => handleBotNoMove()
      }
    }

  private def applyBotMove(move: Move): Unit =
    synchronized {
      val color = currentContext.turn
      val from  = move.from
      val to    = move.to
      currentContext.board.pieceAt(from) match
        case Some(piece) if piece.color == color =>
          val legal = ruleSet.legalMoves(currentContext)(from)
          legal.find(m => m.to == to && m.moveType == move.moveType) match
            case Some(legalMove) => executeMove(legalMove)
            case None =>
              notifyObservers(InvalidMoveEvent(currentContext, s"Bot move ${from}${to} is illegal"))
        case _ =>
          notifyObservers(InvalidMoveEvent(currentContext, "Bot move has invalid source square"))
    }

  private def handleBotNoMove(): Unit =
    synchronized {
      if ruleSet.isCheckmate(currentContext) then
        val winner = currentContext.turn.opposite
        notifyObservers(CheckmateEvent(currentContext, winner))
      else if ruleSet.isStalemate(currentContext) then notifyObservers(DrawEvent(currentContext, DrawReason.Stalemate))
    }

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
