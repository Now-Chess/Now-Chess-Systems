package de.nowchess.bot

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move

object BotMoveRepetition:

  private val maxConsecutiveMoves = 3

  def blockedMoves(context: GameContext): Set[Move] = repeatedMove(context).toSet

  def repeatedMove(context: GameContext): Option[Move] =
    context.moves.takeRight(maxConsecutiveMoves) match
      case first :: second :: third :: Nil if first == second && second == third => Some(first)
      case _                                                                     => None

  def filterAllowed(context: GameContext, moves: List[Move]): List[Move] =
    val blocked = blockedMoves(context)
    moves.filterNot(blocked.contains)
