#!/usr/bin/env python3
"""Label positions with Stockfish evaluations and analyze distribution."""

import json
import chess.engine
import sys
import os
import numpy as np
from pathlib import Path
from tqdm import tqdm
from multiprocessing import Pool
from functools import partial

def normalize_evaluation(cp_value, method='tanh', scale=300.0):
    """Normalize centipawn evaluation to a bounded range.

    Args:
        cp_value: Centipawn evaluation from Stockfish
        method: 'tanh' (default) or 'sigmoid'
        scale: Scale factor (tanh: 300 is typical)

    Returns:
        Normalized value in approximately [-1, 1] (tanh) or [0, 1] (sigmoid)
    """
    if method == 'tanh':
        return np.tanh(cp_value / scale)
    elif method == 'sigmoid':
        return 1.0 / (1.0 + np.exp(-cp_value / scale))
    else:
        return cp_value / 100.0

def _evaluate_fen_batch(args):
    """Worker function to evaluate a batch of FENs with Stockfish threading.

    Args:
        args: tuple of (fens, stockfish_path, depth, normalize)

    Returns:
        list of (fen, eval_normalized, eval_raw) tuples
    """
    fens, stockfish_path, depth, normalize = args

    results = []

    try:
        engine = chess.engine.SimpleEngine.popen_uci(stockfish_path)
    except Exception:
        return []

    try:
        for fen in fens:
            try:
                board = chess.Board(fen)
                if not board.is_valid():
                    continue

                info = engine.analyse(board, chess.engine.Limit(depth=depth))

                if info.get('score') is None:
                    continue

                score = info['score'].white()

                if score.is_mate():
                    eval_cp = 2000 if score.mate() > 0 else -2000
                else:
                    eval_cp = score.cp

                eval_cp = max(-2000, min(2000, eval_cp))
                eval_normalized = normalize_evaluation(eval_cp) if normalize else eval_cp

                results.append((fen, eval_normalized, eval_cp))

            except Exception:
                continue
    finally:
        engine.quit()

    return results


def label_positions_with_stockfish(positions_file, output_file, stockfish_path, batch_size=1000, depth=12, verbose=False, normalize=True, num_workers=1):
    """Read positions and label them with Stockfish evaluations.

    Args:
        positions_file: Path to positions.txt
        output_file: Path to training_data.jsonl
        stockfish_path: Path to stockfish binary
        batch_size: Batch size for processing (positions per worker task, default: 1000)
        depth: Stockfish depth
        verbose: Print detailed error messages
        normalize: If True, normalize evals using tanh
        num_workers: Number of parallel Stockfish processes
    """

    # Check if stockfish exists
    if not Path(stockfish_path).exists():
        print(f"Error: Stockfish not found at {stockfish_path}")
        print(f"Tried: {stockfish_path}")
        print(f"Set STOCKFISH_PATH environment variable or pass as argument")
        sys.exit(1)

    print(f"Using Stockfish: {stockfish_path}")
    print(f"Number of workers: {num_workers}")

    # Check if positions file exists
    if not Path(positions_file).exists():
        print(f"Error: Positions file not found at {positions_file}")
        sys.exit(1)

    # Load existing evaluations if resuming
    evaluated_fens = set()
    position_count = 0

    if Path(output_file).exists():
        with open(output_file, 'r') as f:
            for line in f:
                try:
                    data = json.loads(line)
                    evaluated_fens.add(data['fen'])
                    position_count += 1
                except json.JSONDecodeError:
                    pass
        print(f"Resuming from {position_count} already evaluated positions")

    # Load all FENs that need evaluation
    fens_to_evaluate = []
    fens_seen_in_batch = set()  # Track duplicates within current batch
    skipped_invalid = 0
    skipped_duplicate = 0

    with open(positions_file, 'r') as f:
        for fen in f:
            fen = fen.strip()

            if not fen:
                skipped_invalid += 1
                continue

            if fen in evaluated_fens:
                skipped_duplicate += 1
                continue

            if fen in fens_seen_in_batch:
                skipped_duplicate += 1
                continue

            fens_to_evaluate.append(fen)
            fens_seen_in_batch.add(fen)

    total_to_evaluate = len(fens_to_evaluate)
    total_lines = position_count + skipped_duplicate + skipped_invalid + total_to_evaluate

    if total_to_evaluate == 0:
        if position_count == 0:
            print(f"Error: No valid positions to evaluate in {positions_file}")
            sys.exit(1)
        else:
            print(f"All positions already evaluated. No new positions to process.")
            return True

    print(f"Total positions to process: {total_lines}")
    print(f"New positions to evaluate: {total_to_evaluate}")
    print(f"Using depth: {depth}")
    print()

    # Split FENs into batches for workers
    batches = []
    for i in range(0, total_to_evaluate, batch_size):
        batch = fens_to_evaluate[i:i+batch_size]
        batches.append((batch, stockfish_path, depth, normalize))

    # Process batches in parallel
    evaluated = 0
    errors = 0
    raw_evals = []
    normalized_evals = []

    import time
    start_time = time.time()

    with Pool(num_workers) as pool:
        with tqdm(total=total_lines, initial=position_count, desc="Labeling positions") as pbar:
            with open(output_file, 'a') as out:
                for batch_idx, batch_results in enumerate(pool.imap_unordered(_evaluate_fen_batch, batches)):
                    for fen, eval_normalized, eval_cp in batch_results:
                        # Skip if already evaluated in output file during this run
                        if fen in evaluated_fens:
                            continue

                        data = {"fen": fen, "eval": eval_normalized, "eval_raw": eval_cp}
                        out.write(json.dumps(data) + '\n')
                        evaluated_fens.add(fen)  # Track as evaluated
                        evaluated += 1
                        raw_evals.append(eval_cp)
                        normalized_evals.append(eval_normalized)
                        pbar.update(1)

                    # Update progress for any failed evaluations in the batch
                    batch_size_actual = len(batches[0][0]) if batches else batch_size
                    failed = batch_size_actual - len(batch_results)
                    if failed > 0:
                        errors += failed
                        pbar.update(failed)

                    # Calculate and show throughput and ETA
                    elapsed = time.time() - start_time
                    throughput = evaluated / elapsed if elapsed > 0 else 0
                    remaining_positions = total_to_evaluate - evaluated
                    eta_seconds = remaining_positions / throughput if throughput > 0 else 0
                    eta_str = f"{int(eta_seconds // 60)}:{int(eta_seconds % 60):02d}"

                    if (batch_idx + 1) % max(1, len(batches) // 10) == 0:
                        pbar.set_postfix({
                            'rate': f'{throughput:.0f} pos/s',
                            'eta': eta_str
                        })

    # Print summary and analysis
    print()
    print("=" * 60)
    print("LABELING SUMMARY")
    print("=" * 60)
    print(f"Successfully evaluated: {evaluated}")
    print(f"Skipped (duplicates):   {skipped_duplicate}")
    print(f"Skipped (invalid):      {skipped_invalid}")
    print(f"Errors:                 {errors}")
    print(f"Total processed:        {evaluated + skipped_duplicate + skipped_invalid + errors}")
    print("=" * 60)
    print()

    if evaluated == 0:
        print("WARNING: No positions were successfully evaluated!")
        print("Check that:")
        print("  1. positions.txt is not empty")
        print("  2. positions.txt contains valid FENs")
        print("  3. Stockfish is installed and working")
        print("  4. Stockfish path is correct")
        return False

    # Print distribution analysis
    if raw_evals:
        raw_evals_arr = np.array(raw_evals)
        norm_evals_arr = np.array(normalized_evals)

        print("=" * 60)
        print("EVALUATION DISTRIBUTION ANALYSIS")
        print("=" * 60)
        print()
        print("Raw Evaluations (centipawns):")
        print(f"  Min:    {raw_evals_arr.min():.1f}")
        print(f"  Max:    {raw_evals_arr.max():.1f}")
        print(f"  Mean:   {raw_evals_arr.mean():.1f}")
        print(f"  Median: {np.median(raw_evals_arr):.1f}")
        print(f"  Std:    {raw_evals_arr.std():.1f}")
        print()

        print("Normalized Evaluations (tanh):")
        print(f"  Min:    {norm_evals_arr.min():.4f}")
        print(f"  Max:    {norm_evals_arr.max():.4f}")
        print(f"  Mean:   {norm_evals_arr.mean():.4f}")
        print(f"  Median: {np.median(norm_evals_arr):.4f}")
        print(f"  Std:    {norm_evals_arr.std():.4f}")
        print()

        # Distribution buckets
        print("Raw Evaluation Buckets (counts):")
        buckets = [
            (-float('inf'), -500, "< -5.00"),
            (-500, -300, "[-5.00, -3.00)"),
            (-300, -100, "[-3.00, -1.00)"),
            (-100, 0, "[-1.00, 0.00)"),
            (0, 100, "[0.00, 1.00)"),
            (100, 300, "[1.00, 3.00)"),
            (300, 500, "[3.00, 5.00)"),
            (500, float('inf'), "> 5.00"),
        ]
        for low, high, label in buckets:
            count = np.sum((raw_evals_arr > low) & (raw_evals_arr <= high))
            pct = 100.0 * count / len(raw_evals_arr)
            print(f"  {label}: {count:6d} ({pct:5.1f}%)")

        print("=" * 60)
        print()

    print(f"✓ Labeling complete. Output saved to {output_file}")
    return True

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Label chess positions with Stockfish evaluations")
    parser.add_argument("positions_file", nargs="?", default="positions.txt",
                        help="Input positions file (default: positions.txt)")
    parser.add_argument("output_file", nargs="?", default="training_data.jsonl",
                        help="Output file (default: training_data.jsonl)")
    parser.add_argument("stockfish_path", nargs="?", default=None,
                        help="Path to Stockfish binary (default: $STOCKFISH_PATH or 'stockfish')")
    parser.add_argument("--depth", type=int, default=12,
                        help="Stockfish depth (default: 12)")
    parser.add_argument("--batch-size", type=int, default=1000,
                        help="Batch size for processing (default: 1000)")
    parser.add_argument("--no-normalize", action="store_true",
                        help="Disable evaluation normalization (keep raw centipawns)")
    parser.add_argument("--verbose", action="store_true",
                        help="Print detailed error messages")
    parser.add_argument("--workers", type=int, default=1,
                        help="Number of parallel Stockfish processes (default: 1)")

    args = parser.parse_args()

    # Determine Stockfish path
    stockfish_path = args.stockfish_path or os.environ.get("STOCKFISH_PATH", "stockfish")

    success = label_positions_with_stockfish(
        positions_file=args.positions_file,
        output_file=args.output_file,
        stockfish_path=stockfish_path,
        batch_size=args.batch_size,
        depth=args.depth,
        normalize=not args.no_normalize,
        verbose=args.verbose,
        num_workers=args.workers
    )

    sys.exit(0 if success else 1)
