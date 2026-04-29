#!/usr/bin/env python3
"""Dataset versioning and management for NNUE training data."""

import json
from pathlib import Path
from datetime import datetime
from typing import Optional, Dict, List, Tuple
from rich.console import Console
from rich.table import Table


def get_datasets_dir() -> Path:
    """Get/create datasets directory."""
    datasets_dir = Path(__file__).parent.parent / "datasets"
    datasets_dir.mkdir(exist_ok=True)
    return datasets_dir


def next_dataset_version() -> int:
    """Find the next available dataset version number."""
    datasets_dir = get_datasets_dir()
    versions = []

    for d in datasets_dir.iterdir():
        if d.is_dir() and d.name.startswith("ds_v"):
            try:
                v = int(d.name.split("_v")[1])
                versions.append(v)
            except (ValueError, IndexError):
                pass

    return max(versions) + 1 if versions else 1


def list_datasets() -> List[Tuple[int, Dict]]:
    """List all datasets with their metadata.

    Returns:
        List of (version, metadata_dict) tuples, sorted by version.
    """
    datasets_dir = get_datasets_dir()
    datasets = []

    for d in datasets_dir.iterdir():
        if d.is_dir() and d.name.startswith("ds_v"):
            try:
                v = int(d.name.split("_v")[1])
                metadata_file = d / "metadata.json"
                if metadata_file.exists():
                    with open(metadata_file, 'r') as f:
                        metadata = json.load(f)
                    datasets.append((v, metadata))
            except (ValueError, IndexError, json.JSONDecodeError):
                pass

    return sorted(datasets, key=lambda x: x[0])


def load_dataset_metadata(version: int) -> Optional[Dict]:
    """Load metadata for a specific dataset version.

    Returns:
        Metadata dict or None if not found.
    """
    datasets_dir = get_datasets_dir()
    metadata_file = datasets_dir / f"ds_v{version}" / "metadata.json"

    if not metadata_file.exists():
        return None

    with open(metadata_file, 'r') as f:
        return json.load(f)


def save_dataset_metadata(version: int, metadata: Dict) -> None:
    """Save metadata for a dataset version."""
    datasets_dir = get_datasets_dir()
    dataset_dir = datasets_dir / f"ds_v{version}"
    dataset_dir.mkdir(exist_ok=True)

    metadata_file = dataset_dir / "metadata.json"
    with open(metadata_file, 'w') as f:
        json.dump(metadata, f, indent=2, default=str)


def create_dataset(
    version: int,
    labeled_jsonl_path: str,
    sources: List[Dict],
    stockfish_depth: int = 12
) -> Path:
    """Create a new versioned dataset.

    Args:
        version: Dataset version number
        labeled_jsonl_path: Path to labeled.jsonl to copy
        sources: List of source dicts (see plan for schema)
        stockfish_depth: Depth used for labeling

    Returns:
        Path to the created dataset directory.
    """
    datasets_dir = get_datasets_dir()
    dataset_dir = datasets_dir / f"ds_v{version}"
    dataset_dir.mkdir(exist_ok=True)

    # Copy labeled data with deduplication (in case source has duplicates)
    source_path = Path(labeled_jsonl_path)
    if source_path.exists():
        dest_path = dataset_dir / "labeled.jsonl"
        seen_fens = set()
        unique_count = 0

        with open(source_path, 'r') as src, open(dest_path, 'w') as dst:
            for line in src:
                try:
                    data = json.loads(line)
                    fen = data.get('fen')
                    if fen and fen not in seen_fens:
                        dst.write(line)
                        seen_fens.add(fen)
                        unique_count += 1
                except json.JSONDecodeError:
                    # Skip malformed lines
                    pass

    # Count positions
    total_positions = 0
    if (dataset_dir / "labeled.jsonl").exists():
        with open(dataset_dir / "labeled.jsonl", 'r') as f:
            total_positions = sum(1 for _ in f)

    # Create metadata
    metadata = {
        "version": version,
        "created": datetime.now().isoformat(),
        "total_positions": total_positions,
        "stockfish_depth": stockfish_depth,
        "sources": sources
    }

    save_dataset_metadata(version, metadata)
    return dataset_dir


def extend_dataset(
    version: int,
    new_labeled_path: str,
    new_source_entry: Dict
) -> bool:
    """Extend an existing dataset with new labeled positions (with deduplication).

    Args:
        version: Dataset version to extend
        new_labeled_path: Path to new labeled.jsonl to merge
        new_source_entry: Source entry to add to metadata

    Returns:
        True if successful, False otherwise.
    """
    datasets_dir = get_datasets_dir()
    dataset_dir = datasets_dir / f"ds_v{version}"

    if not dataset_dir.exists():
        return False

    labeled_file = dataset_dir / "labeled.jsonl"
    new_labeled_file = Path(new_labeled_path)

    if not new_labeled_file.exists():
        return False

    # Load existing FENs (dedup set) — must load entire file to avoid duplicates
    existing_fens = set()
    if labeled_file.exists():
        with open(labeled_file, 'r') as f:
            for line in f:
                try:
                    data = json.loads(line)
                    fen = data.get('fen')
                    if fen:
                        existing_fens.add(fen)
                except json.JSONDecodeError:
                    pass

    # Merge new positions, skipping duplicates
    new_count = 0
    new_lines = []
    with open(new_labeled_file, 'r') as f_new:
        for line in f_new:
            try:
                data = json.loads(line)
                fen = data.get('fen')
                if fen and fen not in existing_fens:
                    new_lines.append(line)
                    existing_fens.add(fen)
                    new_count += 1
            except json.JSONDecodeError:
                pass

    # Append only the new, unique positions
    if new_lines:
        with open(labeled_file, 'a') as f_append:
            for line in new_lines:
                f_append.write(line)

    # Update metadata
    metadata = load_dataset_metadata(version)
    if metadata:
        # Count total positions
        total_positions = 0
        with open(labeled_file, 'r') as f:
            total_positions = sum(1 for _ in f)

        metadata['total_positions'] = total_positions
        # Update the source entry with actual count of new positions added
        new_source_entry['actual_count'] = new_count
        metadata['sources'].append(new_source_entry)
        save_dataset_metadata(version, metadata)

    return True


def get_dataset_labeled_path(version: int) -> Optional[Path]:
    """Get the path to a dataset's labeled.jsonl file.

    Returns:
        Path to labeled.jsonl or None if dataset doesn't exist.
    """
    datasets_dir = get_datasets_dir()
    labeled_file = datasets_dir / f"ds_v{version}" / "labeled.jsonl"

    if labeled_file.exists():
        return labeled_file
    return None


def delete_dataset(version: int) -> bool:
    """Delete a dataset (recursively removes directory).

    Args:
        version: Dataset version to delete

    Returns:
        True if successful.
    """
    datasets_dir = get_datasets_dir()
    dataset_dir = datasets_dir / f"ds_v{version}"

    if not dataset_dir.exists():
        return False

    import shutil
    shutil.rmtree(dataset_dir)
    return True


def show_datasets_table(console: Console = None) -> None:
    """Display all datasets in a Rich table."""
    if console is None:
        console = Console()

    datasets = list_datasets()

    if not datasets:
        console.print("[yellow]ℹ No datasets found yet[/yellow]")
        return

    table = Table(title="Available Datasets", show_header=True, header_style="bold cyan")
    table.add_column("Version", style="dim")
    table.add_column("Positions", justify="right")
    table.add_column("Sources", justify="left")
    table.add_column("Depth", justify="center")
    table.add_column("Created", justify="left")

    for v, metadata in datasets:
        positions = metadata.get('total_positions', 0)
        sources = metadata.get('sources', [])
        source_str = ", ".join([s.get('type', '?') for s in sources])
        depth = metadata.get('stockfish_depth', '?')
        created = metadata.get('created', '?')
        if created != '?':
            created = created.split('T')[0]  # Just the date

        table.add_row(f"v{v}", f"{positions:,}", source_str, str(depth), created)

    console.print(table)
