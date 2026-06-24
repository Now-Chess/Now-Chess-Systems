#!/usr/bin/env python3
"""Build the ENTIRE NNUE training dataset with one command.

Orchestrates the existing source modules (Lichess eval DB, self-play, tactical puzzles,
random filler), labels what needs labeling with local Stockfish, deduplicates, balances
the eval distribution, writes append-only compressed shards + a manifest, and pushes to
Google Drive with rclone.

    ./dataset.sh                 # build everything + push
    ./dataset.sh --no-push       # build only
    ./dataset.sh --no-lichess    # skip the (large) Lichess backbone

Tune the CONFIG block below — that is the only thing you normally touch.
"""

import argparse
import hashlib
import json
import os
import random
import subprocess
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

import zstandard as zstd

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "src"))

from generate import play_random_game_and_collect_positions
from label import label_positions_with_stockfish
from lichess_importer import import_lichess_evals
from tactical_positions_extractor import download_and_extract_puzzle_db, extract_tactical_only

# ── CONFIG — the only knobs you normally touch ───────────────────────────────
LICHESS_POSITIONS = 2_000_000      # backbone positions from the Lichess eval DB
USE_SELFPLAY      = True           # label data/selfplay.txt if present
TACTICAL_PUZZLES  = 200_000        # tactical positions from the Lichess puzzle DB
RANDOM_FILLER     = 100_000        # cheap random-play positions
STOCKFISH_DEPTH   = 14             # local labeling depth (selfplay/tactical/random)
RCLONE_REMOTE     = "gdrive:NowChess/datasets"
# ─────────────────────────────────────────────────────────────────────────────

LABEL_BATCH       = 64             # positions per Stockfish task (small = smooth progress + load balance)
SHARD_SIZE        = 100_000        # positions per shard
BALANCE_BINS      = 64             # eval histogram bins over [-1, 1]
BALANCE_FACTOR    = 2.0            # cap each bin at FACTOR x the uniform bin size
LICHESS_EVAL_URL  = "https://database.lichess.org/lichess_db_eval.jsonl.zst"

STOCKFISH_PATH = os.environ.get("STOCKFISH_PATH", "/usr/games/stockfish")
WORKERS        = os.cpu_count() or 4

DATA_DIR     = HERE / "data"
WORK_DIR     = HERE / "data" / "_build"
DATASETS_DIR = HERE / "datasets"
SHARDS_DIR   = DATASETS_DIR / "shards"
MANIFEST     = DATASETS_DIR / "manifest.json"
LICHESS_DB   = HERE / "trainingdata" / "lichess_db_eval.jsonl.zst"


def label(fens_file: Path, out: Path) -> int:
    """Label a FEN file with local Stockfish. Returns positions written."""
    if not fens_file.exists():
        return 0
    label_positions_with_stockfish(
        str(fens_file), str(out), STOCKFISH_PATH,
        batch_size=LABEL_BATCH, depth=STOCKFISH_DEPTH, num_workers=WORKERS,
    )
    return count_lines(out)


def count_lines(path: Path) -> int:
    if not path.exists():
        return 0
    with open(path) as f:
        return sum(1 for _ in f)


def source_lichess(out: Path) -> int:
    if not LICHESS_DB.exists():
        print(f"Downloading Lichess eval DB → {LICHESS_DB} (large, one-time)...")
        LICHESS_DB.parent.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(LICHESS_EVAL_URL, LICHESS_DB)
    return import_lichess_evals(str(LICHESS_DB), str(out), max_positions=LICHESS_POSITIONS)


def source_selfplay(out: Path) -> int:
    return label(DATA_DIR / "selfplay.txt", out)


def source_tactical(out: Path) -> int:
    puzzle_csv = download_and_extract_puzzle_db(output_dir=str(HERE / "tactical_data"))
    if puzzle_csv is None:
        return 0
    fens = WORK_DIR / "tactical_fens.txt"
    extract_tactical_only(str(puzzle_csv), str(fens), max_puzzles=TACTICAL_PUZZLES)
    return label(fens, out)


def source_random(out: Path) -> int:
    fens = WORK_DIR / "random_fens.txt"
    play_random_game_and_collect_positions(
        str(fens), total_positions=RANDOM_FILLER, num_workers=WORKERS,
    )
    return label(fens, out)


def build_sources(args) -> dict[str, Path]:
    """Run each enabled source into its own labeled jsonl. Returns {name: path}."""
    WORK_DIR.mkdir(parents=True, exist_ok=True)
    plan = [
        ("lichess", args.lichess, source_lichess),
        ("selfplay", args.selfplay, source_selfplay),
        ("tactical", args.tactical, source_tactical),
        ("random", args.random, source_random),
    ]
    outputs: dict[str, Path] = {}
    for name, enabled, fn in plan:
        if not enabled:
            continue
        out = WORK_DIR / f"{name}_labeled.jsonl"
        out.unlink(missing_ok=True)
        print(f"\n=== Source: {name} ===")
        written = fn(out)
        print(f"{name}: {written:,} labeled positions")
        if written:
            outputs[name] = out
    return outputs


def existing_fens() -> set[str]:
    """FENs already present in the dataset, so growth stays deduplicated."""
    seen: set[str] = set()
    if not MANIFEST.exists():
        return seen
    manifest = json.loads(MANIFEST.read_text())
    for shard in manifest.get("shards", []):
        for rec in read_shard(SHARDS_DIR / shard["file"]):
            seen.add(rec["fen"])
    return seen


def read_shard(path: Path):
    dctx = zstd.ZstdDecompressor()
    with open(path, "rb") as fh, dctx.stream_reader(fh) as reader:
        for line in iter_text(reader):
            yield json.loads(line)


def iter_text(reader):
    import io
    yield from io.TextIOWrapper(reader, encoding="utf-8")


def merge_dedup(outputs: dict[str, Path], skip: set[str]):
    """Merge all source jsonl, drop dupes (within batch + vs existing dataset)."""
    seen = set(skip)
    records, per_source = [], {}
    for name, path in outputs.items():
        kept = 0
        with open(path) as f:
            for line in f:
                rec = json.loads(line)
                fen = rec["fen"]
                if fen in seen:
                    continue
                seen.add(fen)
                rec["source"] = name
                records.append(rec)
                kept += 1
        per_source[name] = kept
    return records, per_source


def balance(records: list) -> list:
    """Flatten the eval histogram: cap each bin at FACTOR x the uniform bin size."""
    if not records:
        return records
    cap = max(1, int(BALANCE_FACTOR * len(records) / BALANCE_BINS))
    bins: dict[int, int] = {}
    kept = []
    random.shuffle(records)
    for rec in records:
        b = min(BALANCE_BINS - 1, int((rec["eval"] + 1.0) / 2.0 * BALANCE_BINS))
        if bins.get(b, 0) < cap:
            bins[b] = bins.get(b, 0) + 1
            kept.append(rec)
    return kept


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def write_shards(records: list, build_id: str) -> list[dict]:
    SHARDS_DIR.mkdir(parents=True, exist_ok=True)
    cctx = zstd.ZstdCompressor(level=10)
    entries = []
    for i in range(0, len(records), SHARD_SIZE):
        chunk = records[i : i + SHARD_SIZE]
        name = f"{build_id}_{i // SHARD_SIZE:05d}.jsonl.zst"
        path = SHARDS_DIR / name
        with open(path, "wb") as fh, cctx.stream_writer(fh) as w:
            for rec in chunk:
                w.write((json.dumps(rec) + "\n").encode("utf-8"))
        entries.append({"file": name, "positions": len(chunk),
                        "sha256": sha256(path), "build_id": build_id})
        print(f"  wrote {name} ({len(chunk):,} positions)")
    return entries


def update_manifest(new_shards: list[dict], build: dict) -> None:
    manifest = json.loads(MANIFEST.read_text()) if MANIFEST.exists() else {
        "dataset_version": 0, "scale": 300.0, "builds": [], "shards": [],
    }
    manifest["dataset_version"] += 1
    manifest["created"] = build["created"]
    manifest["builds"].append(build)
    manifest["shards"].extend(new_shards)
    manifest["total_positions"] = sum(s["positions"] for s in manifest["shards"])
    MANIFEST.write_text(json.dumps(manifest, indent=2))
    print(f"\nDataset version {manifest['dataset_version']}: "
          f"{manifest['total_positions']:,} total positions across {len(manifest['shards'])} shards")


def push() -> None:
    if not subprocess.run(["which", "rclone"], capture_output=True).stdout:
        print("rclone not found — skipping push.")
        return
    print(f"\nPushing {DATASETS_DIR} → {RCLONE_REMOTE} ...")
    subprocess.run(["rclone", "copy", str(DATASETS_DIR), RCLONE_REMOTE, "--progress"], check=True)


def parse_args():
    p = argparse.ArgumentParser(description="Build the entire NNUE dataset.")
    for name in ("lichess", "selfplay", "tactical", "random", "push"):
        p.add_argument(f"--no-{name}", dest=name, action="store_false")
    p.add_argument("--push-only", action="store_true", help="Push the existing dataset, build nothing.")
    return p.parse_args()


def main() -> None:
    args = parse_args()
    if args.push_only:
        push()
        return
    build_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")

    outputs = build_sources(args)
    if not outputs:
        print("No sources produced data — nothing to build.")
        return

    print("\n=== Merge / dedup / balance ===")
    records, per_source = merge_dedup(outputs, existing_fens())
    print(f"merged unique (new): {len(records):,}")
    records = balance(records)
    print(f"after balancing: {len(records):,}")

    new_shards = write_shards(records, build_id)
    update_manifest(new_shards, {
        "build_id": build_id,
        "created": datetime.now(timezone.utc).isoformat(),
        "stockfish_depth": STOCKFISH_DEPTH,
        "sources": per_source,
        "kept_after_balance": len(records),
    })

    if args.push:
        push()
    print("\nDone.")


if __name__ == "__main__":
    main()
