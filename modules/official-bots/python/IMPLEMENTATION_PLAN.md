# Implementation Plan: Two One-Liner Tools (self-play + dataset)

Goal: **two tools, two start scripts, minimal params.**

```
./selfplay.sh      # bot plays games against itself, writes selfplay FENs       (Scala, standalone)
./dataset.sh       # builds the ENTIRE training dataset + rclone push to Drive   (Python, one script)
```

Both default-everything. Optional first positional arg only when you want to override
the one number that matters.

---

## Tool 1 — `selfplay.sh` (standalone bot, no microservices)

### Why it can be standalone

`Bot` is just `GameContext => Option[Move]` (`Bot.scala`). `NNUEBot.apply` needs only
`DefaultRules` (rule module) + `EvaluationNNUE` (loads the bundled `.nbai`). No Quarkus,
no coordinator/account/ws. The bot module already depends on `api, rule, io`, and `io`
has `FenExporter` + `GameContext.initial` exists. So a plain JVM `main` can run games
with zero service wiring.

### New file: `SelfPlayMain.scala`

`modules/official-bots/src/main/scala/de/nowchess/bot/selfplay/SelfPlayMain.scala`

Loop per game:

1. Start from `GameContext.initial`.
2. **Opening diversity** — play `R` random legal plies (default 8). Without this,
   NNUEBot vs itself is deterministic → the *same game every time*. Random openings are
   what make the games diverse. (Optional later: seed from polyglot book instead.)
3. Then both sides = `NNUEBot(difficulty)`. Apply moves via `DefaultRules.applyMove`.
4. Stop on `isCheckmate / isStalemate / isInsufficientMaterial / isFiftyMoveRule /
   isThreefoldRepetition`, or ply cap (default 200).
5. Emit one **FEN per ply** (via `FenExporter`), skipping positions where side-to-move
   is in check and terminal positions — same filter philosophy the labeler wants.
6. Append FENs to the output file (one per line) — exactly the format `label.py` reads.

Config = a small `case class` with defaults; read from env/args. Defaults:
`games=2000`, `randomOpeningPlies=8`, `maxPlies=200`, `out=python/data/selfplay.txt`,
`threads = availableProcessors`. Parallelize games across threads (each game is
independent; bot is pure).

Output is **FENs only** — labeling happens in Tool 2 with Stockfish. Keeps the bot tool
single-responsibility and fast.

### Gradle: a plain run task (not Quarkus)

Add to `modules/official-bots/build.gradle.kts`:

```kotlin
tasks.register<JavaExec>("selfPlay") {
    group = "nnue"
    mainClass.set("de.nowchess.bot.selfplay.SelfPlayMain")
    classpath = sourceSets["main"].runtimeClasspath
    args(project.findProperty("spArgs")?.toString()?.split(" ") ?: emptyList())
}
```

### `selfplay.sh` (repo `python/` dir)

```bash
#!/usr/bin/env bash
set -euo pipefail
GAMES="${1:-2000}"
cd "$(dirname "$0")/../../.."        # repo root
./gradlew -q :official-bots:selfPlay -PspArgs="--games $GAMES --out modules/official-bots/python/data/selfplay.txt"
echo "Self-play FENs -> modules/official-bots/python/data/selfplay.txt"
```

Usage:

```bash
./selfplay.sh          # 2000 games, bundled net
./selfplay.sh 8000     # more games
```

---

## Tool 2 — `dataset.sh` → `build_dataset.py` (builds EVERYTHING)

One Python script that produces a complete, sharded, pushed dataset. No TUI, no
multi-step menus. It runs the whole data plane end to end:

```
lichess eval DB ─┐
selfplay.txt    ─┼─► label (local Stockfish, skip already-labeled) ─► dedup ─►
tactical        ─┤                                                   eval-bucket
random filler   ─┘                                                   balance ─►
                                              write shards/*.jsonl.zst + manifest.json ─► rclone push
```

### New file: `build_dataset.py` (top-level `python/`)

Reuses existing modules — orchestrates, doesn't reinvent:

- **Backbone:** `lichess_importer.py` — download + sample N pre-labeled positions from
  the Lichess eval DB (no Stockfish cost).
- **Self-play:** read `data/selfplay.txt` FENs → `label.py` with local Stockfish
  (depth 18, all cores — your box eats this).
- **Tactical:** `tactical_positions_extractor.py` → `label.py`.
- **Random filler:** `generate.py` (small cap) → `label.py`.
- **Merge:** dedup by FEN across all sources; **eval-bucket balancing** (cap positions
  per eval bin so near-equal positions don't dominate).
- **Shard + manifest:** split into `shards/*.jsonl.zst` (~100k positions each) + write
  `manifest.json` (positions, sha256, source, net, depth per shard). Append-only:
  existing shards untouched, new run adds shards + entries (the scaling story from the
  concept).
- **Push:** `rclone copy datasets/ gdrive:NowChess/datasets` — ships only new shards.

### One config block, sane defaults

Top of the script — the *only* thing you ever touch:

```python
LICHESS_POSITIONS = 2_000_000   # backbone
USE_SELFPLAY      = True        # reads data/selfplay.txt if present
TACTICAL_PUZZLES  = 200_000
RANDOM_FILLER     = 100_000
STOCKFISH_DEPTH   = 18
RCLONE_REMOTE     = "gdrive:NowChess/datasets"
```

Everything else (paths, workers=all cores, shard size, balancing bins) is internal.

### `dataset.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
python build_dataset.py "$@"
```

Usage:

```bash
./dataset.sh          # full dataset (lichess + selfplay + tactical + filler) -> Drive
```

That single command: downloads backbone, labels self-play/tactical/filler, dedups,
balances, shards, and rclone-pushes to Drive. Colab then syncs (concept doc §3).

---

## End-to-end loop (the flywheel)

```
./selfplay.sh        # bot generates games with the current net
./dataset.sh         # fold them into a new dataset version, push to Drive
# (Colab) sync + train -> export nnue_weights.nbai
# drop .nbai into modules/official-bots/src/main/resources/, rebuild
./selfplay.sh        # next net plays stronger, better games... repeat
```

---

## Build order

1. `SelfPlayMain.scala` — standalone game loop, random openings, parallel games, FEN out.
2. `selfPlay` Gradle `JavaExec` task + `selfplay.sh`.
3. `build_dataset.py` — orchestrate existing importer/label/tactical/generate into
   shards + manifest; rclone push.
4. `dataset.sh`.
5. Shard/manifest read support in `dataset.py` + zstd streaming loader in `train.py`
   (consumed on Colab).
6. Notebook: single "sync dataset version" cell, ephemeral `/content` clone.

## Decisions to confirm

- **Self-play opponent:** NNUEBot vs itself + random openings (planned). Add vs-Stockfish
  later if more decisive games wanted.
- **Self-play net source:** use the `.nbai` bundled in `resources` (simplest), or accept
  a `--weights path`? Plan = bundled by default.
- **rclone remote name:** confirm `gdrive` is your configured rclone remote, and the
  target folder `NowChess/datasets`.
- **Stockfish path on your box:** `$STOCKFISH_PATH` or `/usr/games/stockfish`?
