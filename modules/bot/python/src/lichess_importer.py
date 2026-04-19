#!/usr/bin/env python3
"""Import pre-labeled positions from the Lichess evaluation database.

Source: https://database.lichess.org/#evals
Format: lichess_db_eval.jsonl.zst — compressed JSONL, one position per line.

Each line:
  {
    "fen": "<pieces> <turn> <castling> <ep>",
    "evals": [
      {
        "knodes": <int>,
        "depth": <int>,
        "pvs": [{"cp": <int>, "line": "..."} | {"mate": <int>, "line": "..."}]
      }
    ]
  }

cp and mate are from White's perspective (positive = White winning), matching
the sign convention used by label.py (score.white()) and expected by train.py.
"""

import json
import sys
import numpy as np
from pathlib import Path
from tqdm import tqdm

MATE_CP = 20000
SCALE   = 300.0


def _best_eval(evals: list) -> dict | None:
    """Return the highest-depth evaluation entry, using knodes as tiebreaker."""
    if not evals:
        return None
    return max(evals, key=lambda e: (e.get("depth", 0), e.get("knodes", 0)))


def _cp_from_pv(pv: dict) -> int | None:
    """Extract centipawn value from a principal variation entry."""
    if "cp" in pv:
        return max(-MATE_CP, min(MATE_CP, pv["cp"]))
    if "mate" in pv:
        return MATE_CP if pv["mate"] > 0 else -MATE_CP
    return None


def _normalize(cp: int) -> float:
    return float(np.tanh(cp / SCALE))


def import_lichess_evals(
    input_path: str,
    output_file: str,
    max_positions: int | None = None,
    min_depth: int = 0,
    verbose: bool = False,
) -> int:
    """Stream the Lichess eval database and write a labeled.jsonl file.

    Args:
        input_path:    Path to lichess_db_eval.jsonl.zst (or uncompressed .jsonl).
        output_file:   Destination labeled.jsonl (appended — supports resuming).
        max_positions: Stop after this many new positions (None = no limit).
        min_depth:     Skip positions whose best eval has depth < min_depth.
        verbose:       Print warnings for skipped lines.

    Returns:
        Number of new positions written.
    """
    import zstandard as zstd

    input_path = Path(input_path)
    if not input_path.exists():
        print(f"Error: {input_path} not found")
        sys.exit(1)

    # Resume: collect already-written FENs so we skip duplicates.
    seen_fens: set[str] = set()
    if Path(output_file).exists():
        with open(output_file, "r") as f:
            for line in f:
                try:
                    seen_fens.add(json.loads(line)["fen"])
                except (json.JSONDecodeError, KeyError):
                    pass
        if seen_fens:
            print(f"Resuming — skipping {len(seen_fens):,} already-imported positions")

    written = 0
    skipped_depth = 0
    skipped_no_eval = 0
    skipped_dup = 0

    def iter_lines():
        """Yield decoded text lines from either a .zst or plain .jsonl file."""
        import io
        if input_path.suffix == ".zst":
            dctx = zstd.ZstdDecompressor()
            with open(input_path, "rb") as fh:
                with dctx.stream_reader(fh) as reader:
                    text_stream = io.TextIOWrapper(reader, encoding="utf-8")
                    yield from text_stream
        else:
            with open(input_path, "r", encoding="utf-8") as fh:
                yield from fh

    try:
        with open(output_file, "a") as out:
            with tqdm(desc="Importing Lichess evals", unit=" pos") as pbar:
                for raw_line in iter_lines():
                    line = raw_line.strip()
                    if not line:
                        continue

                    try:
                        data = json.loads(line)
                    except json.JSONDecodeError:
                        if verbose:
                            print("Warning: malformed JSON line skipped")
                        continue

                    fen = data.get("fen", "")
                    if not fen:
                        skipped_no_eval += 1
                        continue

                    if fen in seen_fens:
                        skipped_dup += 1
                        continue

                    best = _best_eval(data.get("evals", []))
                    if best is None:
                        skipped_no_eval += 1
                        continue

                    if best.get("depth", 0) < min_depth:
                        skipped_depth += 1
                        continue

                    pvs = best.get("pvs", [])
                    if not pvs:
                        skipped_no_eval += 1
                        continue

                    cp = _cp_from_pv(pvs[0])
                    if cp is None:
                        skipped_no_eval += 1
                        continue

                    record = {
                        "fen":      fen,
                        "eval":     _normalize(cp),
                        "eval_raw": cp,
                    }
                    out.write(json.dumps(record) + "\n")
                    seen_fens.add(fen)
                    written += 1
                    pbar.update(1)

                    if max_positions and written >= max_positions:
                        print(f"\nReached max_positions limit ({max_positions:,})")
                        break

    except Exception:
        raise

    print()
    print("=" * 60)
    print("LICHESS IMPORT SUMMARY")
    print("=" * 60)
    print(f"Positions written:  {written:,}")
    print(f"Skipped (dup):      {skipped_dup:,}")
    print(f"Skipped (no eval):  {skipped_no_eval:,}")
    print(f"Skipped (depth<{min_depth}): {skipped_depth:,}")
    print("=" * 60)
    print(f"\n✓ Output: {output_file}")

    return written


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(
        description="Import Lichess pre-labeled positions into labeled.jsonl"
    )
    parser.add_argument("input_path",
                        help="Path to lichess_db_eval.jsonl.zst")
    parser.add_argument("output_file", nargs="?", default="training_data.jsonl",
                        help="Output labeled.jsonl (default: training_data.jsonl)")
    parser.add_argument("--max-positions", type=int, default=None,
                        help="Stop after N positions (default: no limit)")
    parser.add_argument("--min-depth", type=int, default=0,
                        help="Minimum eval depth to accept (default: 0)")
    parser.add_argument("--verbose", action="store_true",
                        help="Print warnings for skipped lines")

    args = parser.parse_args()
    count = import_lichess_evals(
        input_path=args.input_path,
        output_file=args.output_file,
        max_positions=args.max_positions,
        min_depth=args.min_depth,
        verbose=args.verbose,
    )
    sys.exit(0 if count > 0 else 1)
