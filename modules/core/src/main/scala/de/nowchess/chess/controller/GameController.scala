package de.nowchess.chess.controller

import scala.io.StdIn
import de.nowchess.api.board.{Board, Color, File, Piece, Rank, Square}
import de.nowchess.api.game.CastlingRights
import de.nowchess.chess.logic.{GameContext, MoveValidator, GameRules, PositionStatus, CastleSide, withCastle}
import de.nowchess.chess.view.Renderer

// ---------------------------------------------------------------------------
// Result ADT returned by the pure processMove function
// ---------------------------------------------------------------------------

sealed trait MoveResult
object MoveResult:
  case object Quit                                                                       extends MoveResult
  case class  InvalidFormat(raw: String)                                                extends MoveResult
  case object NoPiece                                                                   extends MoveResult
  case object WrongColor                                                                extends MoveResult
  case object IllegalMove                                                               extends MoveResult
  case class  Moved(newCtx: GameContext, captured: Option[Piece], newTurn: Color)      extends MoveResult
  case class  MovedInCheck(newCtx: GameContext, captured: Option[Piece], newTurn: Color) extends MoveResult
  case class  Checkmate(winner: Color)                                                  extends MoveResult
  case object Stalemate                                                                 extends MoveResult

// ---------------------------------------------------------------------------
// Controller
// ---------------------------------------------------------------------------

object GameController:

  /** Pure function: interprets one raw input line against the current game context.
   *  Has no I/O side effects — all output must be handled by the caller.
   */
  def processMove(ctx: GameContext, turn: Color, raw: String): MoveResult =
    raw.trim match
      case "quit" | "q" =>
        MoveResult.Quit
      case trimmed =>
        Parser.parseMove(trimmed) match
          case None =>
            MoveResult.InvalidFormat(trimmed)
          case Some((from, to)) =>
            ctx.board.pieceAt(from) match
              case None =>
                MoveResult.NoPiece
              case Some(piece) if piece.color != turn =>
                MoveResult.WrongColor
              case Some(_) =>
                if !MoveValidator.isLegal(ctx, from, to) then
                  MoveResult.IllegalMove
                else
                  val castleOpt = if MoveValidator.isCastle(ctx.board, from, to)
                                  then Some(MoveValidator.castleSide(from, to))
                                  else None
                  val (newBoard, captured) = castleOpt match
                    case Some(side) => (ctx.board.withCastle(turn, side), None)
                    case None       => ctx.board.withMove(from, to)
                  val newCtx = applyRightsRevocation(
                    ctx.copy(board = newBoard), turn, from, to, castleOpt
                  )
                  GameRules.gameStatus(newCtx, turn.opposite) match
                    case PositionStatus.Normal  => MoveResult.Moved(newCtx, captured, turn.opposite)
                    case PositionStatus.InCheck => MoveResult.MovedInCheck(newCtx, captured, turn.opposite)
                    case PositionStatus.Mated   => MoveResult.Checkmate(turn)
                    case PositionStatus.Drawn   => MoveResult.Stalemate

  private def applyRightsRevocation(
    ctx: GameContext,
    turn: Color,
    from: Square,
    to: Square,
    castle: Option[CastleSide]
  ): GameContext =
    // Step 1: Revoke all rights for a castling move (idempotent with step 2)
    val ctx0 = castle.fold(ctx)(_ => ctx.withUpdatedRights(turn, CastlingRights.None))

    // Step 2: Source-square revocation
    val ctx1 = from match
      case Square(File.E, Rank.R1) => ctx0.withUpdatedRights(Color.White, CastlingRights.None)
      case Square(File.E, Rank.R8) => ctx0.withUpdatedRights(Color.Black, CastlingRights.None)
      case Square(File.A, Rank.R1) => ctx0.withUpdatedRights(Color.White, ctx0.whiteCastling.copy(queenSide = false))
      case Square(File.H, Rank.R1) => ctx0.withUpdatedRights(Color.White, ctx0.whiteCastling.copy(kingSide  = false))
      case Square(File.A, Rank.R8) => ctx0.withUpdatedRights(Color.Black, ctx0.blackCastling.copy(queenSide = false))
      case Square(File.H, Rank.R8) => ctx0.withUpdatedRights(Color.Black, ctx0.blackCastling.copy(kingSide  = false))
      case _                       => ctx0

    // Step 3: Destination-square revocation (enemy captures a rook on its home square)
    to match
      case Square(File.A, Rank.R1) => ctx1.withUpdatedRights(Color.White, ctx1.whiteCastling.copy(queenSide = false))
      case Square(File.H, Rank.R1) => ctx1.withUpdatedRights(Color.White, ctx1.whiteCastling.copy(kingSide  = false))
      case Square(File.A, Rank.R8) => ctx1.withUpdatedRights(Color.Black, ctx1.blackCastling.copy(queenSide = false))
      case Square(File.H, Rank.R8) => ctx1.withUpdatedRights(Color.Black, ctx1.blackCastling.copy(kingSide  = false))
      case _                       => ctx1

  /** Thin I/O shell: renders the board, reads a line, delegates to processMove,
   *  prints the outcome, and recurses until the game ends.
   */
  def gameLoop(ctx: GameContext, turn: Color): Unit =
    println()
    print(Renderer.render(ctx.board))
    println(s"${turn.label}'s turn. Enter move: ")
    val input = Option(StdIn.readLine()).getOrElse("quit").trim
    processMove(ctx, turn, input) match
      case MoveResult.Quit =>
        println("Game over. Goodbye!")
      case MoveResult.InvalidFormat(raw) =>
        println(s"Invalid move format '$raw'. Use coordinate notation, e.g. e2e4.")
        gameLoop(ctx, turn)
      case MoveResult.NoPiece =>
        println(s"No piece on ${Parser.parseMove(input).map(_._1).fold("?")(_.toString)}.")
        gameLoop(ctx, turn)
      case MoveResult.WrongColor =>
        println(s"That is not your piece.")
        gameLoop(ctx, turn)
      case MoveResult.IllegalMove =>
        println(s"Illegal move.")
        gameLoop(ctx, turn)
      case MoveResult.Moved(newCtx, captured, newTurn) =>
        val prevTurn = newTurn.opposite
        captured.foreach: cap =>
          val toSq = Parser.parseMove(input).map(_._2).fold("?")(_.toString)
          println(s"${prevTurn.label} captures ${cap.color.label} ${cap.pieceType.label} on $toSq")
        gameLoop(newCtx, newTurn)
      case MoveResult.MovedInCheck(newCtx, captured, newTurn) =>
        val prevTurn = newTurn.opposite
        captured.foreach: cap =>
          val toSq = Parser.parseMove(input).map(_._2).fold("?")(_.toString)
          println(s"${prevTurn.label} captures ${cap.color.label} ${cap.pieceType.label} on $toSq")
        println(s"${newTurn.label} is in check!")
        gameLoop(newCtx, newTurn)
      case MoveResult.Checkmate(winner) =>
        println(s"Checkmate! ${winner.label} wins.")
        gameLoop(GameContext.initial, Color.White)
      case MoveResult.Stalemate =>
        println("Stalemate! The game is a draw.")
        gameLoop(GameContext.initial, Color.White)
