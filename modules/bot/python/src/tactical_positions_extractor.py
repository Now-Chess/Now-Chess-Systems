import chess
import csv
import json
import sys
import urllib.request
from pathlib import Path
from typing import Set, Tuple

try:
    import zstandard as zstd
except ImportError:
    print("zstandard library not found. Install with: pip install zstandard")
    sys.exit(1)

from generate import play_random_game_and_collect_positions


def download_and_extract_puzzle_db(
        url: str = 'https://database.lichess.org/lichess_db_puzzle.csv.zst',
        output_dir: str = 'tactical_data'
):
    """Download and extract the Lichess puzzle database."""
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    csv_file = output_path / 'lichess_db_puzzle.csv'
    zst_file = output_path / 'lichess_db_puzzle.csv.zst'

    # Download if not already present
    if not zst_file.exists():
        print(f"Downloading puzzle database from {url}...")
        try:
            urllib.request.urlretrieve(url, zst_file)
            print(f"Downloaded to {zst_file}")
        except Exception as e:
            print(f"Failed to download: {e}")
            return None

    # Extract if CSV doesn't exist
    if not csv_file.exists():
        print(f"Extracting {zst_file}...")
        try:
            with open(zst_file, 'rb') as f:
                dctx = zstd.ZstdDecompressor()
                with dctx.stream_reader(f) as reader:
                    with open(csv_file, 'wb') as out:
                        out.write(reader.read())
            print(f"Extracted to {csv_file}")
        except Exception as e:
            print(f"Failed to extract: {e}")
            return None

    return str(csv_file)


def extract_puzzle_positions(
        puzzle_csv: str,
        max_puzzles: int = 300_000
) -> Set[str]:
    """
    Extract the position BEFORE the blunder from each puzzle.
    This is exactly the type of position where tactical
    recognition matters most.

    Returns a set of unique FENs.
    """
    positions = set()

    with open(puzzle_csv) as f:
        reader = csv.DictReader(f)
        for row in reader:
            if len(positions) >= max_puzzles:
                break

            try:
                board = chess.Board(row['FEN'])

                # The puzzle FEN is AFTER the blunder move
                # We want the position BEFORE — so it learns
                # to find the tactic, not just play it
                moves = row['Moves'].split()

                # Undo one move to get pre-tactic position
                board.push_uci(moves[0])  # opponent blunder
                fen = board.fen()

                # Filter for useful tactical themes
                themes = row.get('Themes', '')
                useful = any(t in themes for t in [
                    'fork', 'pin', 'skewer', 'discoveredAttack',
                    'mate', 'mateIn2', 'mateIn3', 'hangingPiece',
                    'trappedPiece', 'sacrifice'
                ])

                if useful:
                    positions.add(fen)

            except Exception:
                continue

    return positions


def load_positions_from_file(file_path: str) -> Set[str]:
    """Load positions from a text file (one FEN per line)."""
    positions = set()
    try:
        with open(file_path) as f:
            for line in f:
                line = line.strip()
                if line:
                    positions.add(line)
        print(f"Loaded {len(positions)} positions from {file_path}")
        return positions
    except Exception as e:
        print(f"Failed to load from {file_path}: {e}")
        return set()


def merge_positions(
        tactical: Set[str],
        other: Set[str],
        output_file: str = 'position.txt'
):
    """Merge two position sets and write to file."""
    merged = tactical | other

    with open(output_file, 'w') as f:
        for fen in merged:
            f.write(fen + '\n')

    overlap = len(tactical & other)
    print(f"\n{'='*60}")
    print(f"MERGE SUMMARY")
    print(f"{'='*60}")
    print(f"Tactical positions:        {len(tactical):,}")
    print(f"Other positions:           {len(other):,}")
    print(f"Overlap (deduplicated):    {overlap:,}")
    print(f"Total merged positions:    {len(merged):,}")
    print(f"Written to:                {output_file}")
    print(f"{'='*60}\n")


def extract_tactical_only(
        puzzle_csv: str,
        output_file: str,
        max_puzzles: int = 300_000
) -> int:
    """Extract tactical positions and save to file (no merge prompts).

    Args:
        puzzle_csv: Path to Lichess puzzle CSV
        output_file: Where to save the FEN positions
        max_puzzles: Maximum puzzles to extract

    Returns:
        Number of positions extracted
    """
    print("Extracting tactical positions from puzzle database...")
    tactical_positions = extract_puzzle_positions(puzzle_csv, max_puzzles)

    with open(output_file, 'w') as f:
        for fen in tactical_positions:
            f.write(fen + '\n')

    return len(tactical_positions)


def interactive_merge_positions(
        puzzle_csv: str,
        output_file: str = 'position.txt',
        max_puzzles: int = 300_000
):
    """Interactive workflow: extract tactical positions and merge with user selection."""
    print("\n" + "="*60)
    print("TACTICAL POSITION EXTRACTOR & MERGER")
    print("="*60 + "\n")

    # Extract tactical positions
    print("Extracting tactical positions from puzzle database...")
    tactical_positions = extract_puzzle_positions(puzzle_csv, max_puzzles)
    print(f"Extracted {len(tactical_positions):,} unique tactical positions\n")

    # Ask what to merge with
    print("What would you like to merge with these tactical positions?")
    print("1. Load from a position file")
    print("2. Generate random positions")
    print("3. Skip merging (save tactical only)")

    choice = input("\nEnter choice (1-3): ").strip()

    other_positions = set()

    if choice == '1':
        file_path = input("Enter path to position file: ").strip()
        other_positions = load_positions_from_file(file_path)

    elif choice == '2':
        positions_to_gen = input("How many positions to generate? (default 1000000): ").strip()
        try:
            positions_to_gen = int(positions_to_gen) if positions_to_gen else 1000000
        except ValueError:
            positions_to_gen = 1000000

        temp_file = 'temp_generated_positions.txt'
        print(f"\nGenerating {positions_to_gen:,} random positions...")
        play_random_game_and_collect_positions(
            output_file=temp_file,
            total_positions=positions_to_gen,
            samples_per_game=1,
            min_move=1,
            max_move=50,
            num_workers=8
        )
        other_positions = load_positions_from_file(temp_file)

    elif choice == '3':
        print("Skipping merge, saving tactical positions only...")

    else:
        print("Invalid choice, saving tactical positions only...")

    merge_positions(tactical_positions, other_positions, output_file)


if __name__ == '__main__':
    import argparse

    parser = argparse.ArgumentParser(description="Extract and merge tactical positions")
    parser.add_argument("--url", default='https://database.lichess.org/lichess_db_puzzle.csv.zst',
                        help="URL to download puzzle database from")
    parser.add_argument("--output-dir", default='trainingdata',
                        help="Directory to extract puzzle database to")
    parser.add_argument("--max-puzzles", type=int, default=300_000,
                        help="Maximum puzzles to extract (default: 300000)")
    parser.add_argument("--output-file", default='position.txt',
                        help="Output file for merged positions (default: position.txt)")

    args = parser.parse_args()

    # Download and extract
    csv_path = download_and_extract_puzzle_db(args.url, args.output_dir)

    if csv_path:
        # Interactive merge
        interactive_merge_positions(csv_path, args.output_file, args.max_puzzles)
    else:
        print("Failed to download/extract puzzle database")
        sys.exit(1)