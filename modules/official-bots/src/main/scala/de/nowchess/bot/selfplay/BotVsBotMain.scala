package de.nowchess.bot.selfplay

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.api.rules.RuleSet
import de.nowchess.api.board.Color
import de.nowchess.bot.BotDifficulty
import de.nowchess.bot.bots.{HybridBot, NNUEBot}
import de.nowchess.io.fen.FenExporter
import de.nowchess.rules.sets.DefaultRules

import java.io.{BufferedWriter, FileWriter}
import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.util.Random

enum GameResult:
  case Bot1Wins
  case Bot2Wins
  case Draw

/** Standalone bot-vs-bot harness. Runs two NNUEBots against each other from randomised openings and writes the
  * visited positions as one FEN per line. Each bot can use different difficulty levels and weight files.
  *
  * Games run sequentially. Bots alternate: bot1 plays white, then bot1 plays black, alternating per game to ensure
  * fair evaluation.
  */
object BotVsBotMain:

  private case class Config(
      games: Int = 1,
      out: String = "modules/official-bots/python/data/botvbot.txt",
      weights1: Option[String] = None,
      weights2: Option[String] = None,
      difficulty1: String = "Hard",
      difficulty2: String = "Hard",
      moveTimeMs: Long = 1000L,
      randomPlies: Int = 8,
      maxPlies: Int = 200,
      seed: Long = System.nanoTime(),
  )

  def main(args: Array[String]): Unit =
    val config = parse(args.toList, Config())

    val rules = DefaultRules
    val difficulty1 = parseDifficulty(config.difficulty1)
    val difficulty2 = parseDifficulty(config.difficulty2)

    config.weights1.foreach(System.setProperty("nnue.weights", _))
    val bot1 = HybridBot(difficulty1, rules, searchThreads = 6)

    config.weights2.foreach(System.setProperty("nnue.weights", _))
    val bot2 = HybridBot(difficulty2, rules)

    val rng = new Random(config.seed)
    val seen = mutable.HashSet.empty[String]
    var bot1Wins = 0
    var bot2Wins = 0
    var draws = 0

    Files.createDirectories(Path.of(config.out).toAbsolutePath.getParent)
    val writer = new BufferedWriter(new FileWriter(config.out))
    try
      var game = 0
      while game < config.games do
        val bot1AsWhite = game % 2 == 0
        val result = playGame(rules, bot1, bot2, rng, config, seen, writer, bot1AsWhite)
        game += 1

        result match
          case Some(GameResult.Bot1Wins) =>
            bot1Wins += 1
            println(s"Game $game: Bot1 wins${if bot1AsWhite then " (as white)" else " (as black)"}")
          case Some(GameResult.Bot2Wins) =>
            bot2Wins += 1
            println(s"Game $game: Bot2 wins${if !bot1AsWhite then " (as white)" else " (as black)"}")
          case Some(GameResult.Draw) =>
            draws += 1
            println(s"Game $game: Draw")
          case None => ()

        if game % 25 == 0 then
          writer.flush()
          println(
            s"  Progress: $game/${config.games} | Positions: ${seen.size} | Bot1: $bot1Wins, Bot2: $bot2Wins, Draws: $draws\n"
          )
    finally writer.close()
    println(s"\nFinal Statistics:")
    println(s"  Bot1 wins: $bot1Wins")
    println(s"  Bot2 wins: $bot2Wins")
    println(s"  Draws:     $draws")
    println(s"  Positions: ${seen.size} -> ${config.out}")

  private def playGame(
      rules: RuleSet,
      bot1: GameContext => Option[Move],
      bot2: GameContext => Option[Move],
      rng: Random,
      config: Config,
      seen: mutable.HashSet[String],
      writer: BufferedWriter,
      bot1AsWhite: Boolean,
  ): Option[GameResult] =
    randomOpening(rules, rng, config.randomPlies, GameContext.initial) match
      case None => None
      case Some(start) =>
        var ctx = start
        var plies = config.randomPlies
        var live = true
        while live && plies < config.maxPlies do
          if isTerminal(rules, ctx) then live = false
          else
            val currentBot = if (plies - config.randomPlies) % 2 == 0 then
              if bot1AsWhite then bot1 else bot2
            else if bot1AsWhite then bot2
            else bot1

            currentBot(ctx) match
              case None => live = false
              case Some(move) =>
                ctx = rules.applyMove(ctx)(move)
                plies += 1
                record(rules, ctx, seen, writer)

        determineWinner(rules, ctx, bot1AsWhite)

  private def determineWinner(rules: RuleSet, ctx: GameContext, bot1AsWhite: Boolean): Option[GameResult] =
    val legalMoves = rules.allLegalMoves(ctx)
     
    if legalMoves.nonEmpty then
      Some(GameResult.Draw)
    else if rules.isCheck(ctx) then
      if ctx.turn == (if bot1AsWhite then Color.Black else Color.White) then
        Some(GameResult.Bot1Wins)
      else
        Some(GameResult.Bot2Wins)
    else if rules.isFiftyMoveRule(ctx) || rules.isThreefoldRepetition(ctx) || rules.isInsufficientMaterial(ctx) then
      Some(GameResult.Draw)
    else
      Some(GameResult.Draw)

  private def randomOpening(rules: RuleSet, rng: Random, plies: Int, start: GameContext): Option[GameContext] =
    var ctx = start
    var i = 0
    while i < plies do
      val legal = rules.allLegalMoves(ctx)
      if legal.isEmpty then return None
      ctx = rules.applyMove(ctx)(legal(rng.nextInt(legal.size)))
      i += 1
    Some(ctx)

  private def record(rules: RuleSet, ctx: GameContext, seen: mutable.HashSet[String], writer: BufferedWriter): Unit =
    if !rules.isCheck(ctx) && !isTerminal(rules, ctx) then
      val fen = FenExporter.gameContextToFen(ctx)
      if seen.add(fen) then
        writer.write(fen)
        writer.newLine()

  private def isTerminal(rules: RuleSet, ctx: GameContext): Boolean =
    rules.allLegalMoves(ctx).isEmpty ||
      rules.isInsufficientMaterial(ctx) ||
      rules.isFiftyMoveRule(ctx) ||
      rules.isThreefoldRepetition(ctx)

  private def parse(args: List[String], acc: Config): Config = args match
    case "--games" :: v :: rest        => parse(rest, acc.copy(games = v.toInt))
    case "--out" :: v :: rest          => parse(rest, acc.copy(out = v))
    case "--weights1" :: v :: rest     => parse(rest, acc.copy(weights1 = Some(v)))
    case "--weights2" :: v :: rest     => parse(rest, acc.copy(weights2 = Some(v)))
    case "--difficulty1" :: v :: rest  => parse(rest, acc.copy(difficulty1 = v))
    case "--difficulty2" :: v :: rest  => parse(rest, acc.copy(difficulty2 = v))
    case "--move-ms" :: v :: rest      => parse(rest, acc.copy(moveTimeMs = v.toLong))
    case "--random-plies" :: v :: rest => parse(rest, acc.copy(randomPlies = v.toInt))
    case "--max-plies" :: v :: rest    => parse(rest, acc.copy(maxPlies = v.toInt))
    case "--seed" :: v :: rest         => parse(rest, acc.copy(seed = v.toLong))
    case Nil                           => acc
    case unknown :: rest               => println(s"Ignoring unknown arg: $unknown"); parse(rest, acc)

  private def parseDifficulty(name: String): BotDifficulty = name match
    case "Easy"   => BotDifficulty.Easy
    case "Medium" => BotDifficulty.Medium
    case "Expert" => BotDifficulty.Expert
    case _        => BotDifficulty.Hard
