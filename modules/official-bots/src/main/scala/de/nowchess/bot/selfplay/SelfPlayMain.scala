package de.nowchess.bot.selfplay

import de.nowchess.api.game.GameContext
import de.nowchess.api.move.Move
import de.nowchess.api.rules.RuleSet
import de.nowchess.bot.{Bot, BotDifficulty}
import de.nowchess.bot.bots.NNUEBot
import de.nowchess.io.fen.FenExporter
import de.nowchess.rules.sets.DefaultRules

import java.io.{BufferedWriter, FileWriter}
import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.util.Random

/** Standalone self-play harness. Runs NNUEBot against itself from randomised openings and writes the visited positions
  * as one FEN per line — the input format expected by the Python labeler. No microservices.
  *
  * Games run sequentially because EvaluationNNUE holds a shared accumulator; the small per-move time budget keeps
  * throughput high. Stockfish relabels every position later, so shallow self-play search is sufficient.
  */
object SelfPlayMain:

  private case class Config(
      games: Int = 500,
      out: String = "modules/official-bots/python/data/selfplay.txt",
      weights: Option[String] = None,
      moveTimeMs: Long = 50L,
      randomPlies: Int = 8,
      maxPlies: Int = 200,
      seed: Long = System.nanoTime(),
  )

  def main(args: Array[String]): Unit =
    val config = parse(args.toList, Config())
    config.weights.foreach(System.setProperty("nnue.weights", _))

    val rules = DefaultRules
    val bot   = NNUEBot(BotDifficulty.Hard, rules, fixedMoveTimeMs = Some(config.moveTimeMs))
    val rng   = new Random(config.seed)
    val seen  = mutable.HashSet.empty[String]

    Files.createDirectories(Path.of(config.out).toAbsolutePath.getParent)
    val writer = new BufferedWriter(new FileWriter(config.out))
    try
      var game = 0
      while game < config.games do
        playGame(rules, bot, rng, config, seen, writer)
        game += 1
        if game % 25 == 0 then
          writer.flush()
          println(s"games=$game/${config.games} positions=${seen.size}")
    finally writer.close()
    println(s"Done. ${seen.size} unique positions -> ${config.out}")

  private def playGame(
      rules: RuleSet,
      bot: Bot,
      rng: Random,
      config: Config,
      seen: mutable.HashSet[String],
      writer: BufferedWriter,
  ): Unit =
    randomOpening(rules, rng, config.randomPlies, GameContext.initial) match
      case None => ()
      case Some(start) =>
        var ctx   = start
        var plies = config.randomPlies
        var live  = true
        while live && plies < config.maxPlies do
          if isTerminal(rules, ctx) then live = false
          else
            bot(ctx) match
              case None => live = false
              case Some(move) =>
                ctx = rules.applyMove(ctx)(move)
                plies += 1
                record(rules, ctx, seen, writer)

  private def randomOpening(rules: RuleSet, rng: Random, plies: Int, start: GameContext): Option[GameContext] =
    var ctx = start
    var i   = 0
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
    case "--weights" :: v :: rest      => parse(rest, acc.copy(weights = Some(v)))
    case "--move-ms" :: v :: rest      => parse(rest, acc.copy(moveTimeMs = v.toLong))
    case "--random-plies" :: v :: rest => parse(rest, acc.copy(randomPlies = v.toInt))
    case "--max-plies" :: v :: rest    => parse(rest, acc.copy(maxPlies = v.toInt))
    case "--seed" :: v :: rest         => parse(rest, acc.copy(seed = v.toLong))
    case Nil                           => acc
    case unknown :: rest               => println(s"Ignoring unknown arg: $unknown"); parse(rest, acc)
