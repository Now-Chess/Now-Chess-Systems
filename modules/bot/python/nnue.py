#!/usr/bin/env python3
"""Central NNUE pipeline TUI for training and exporting models."""

import os
import shutil
import sys
import tempfile
from pathlib import Path
from rich.console import Console
from rich.table import Table
from rich.panel import Panel
from rich.prompt import Prompt, Confirm
from rich import print as rprint

# Add src directory to path so we can import modules
sys.path.insert(0, str(Path(__file__).parent / "src"))

from generate import play_random_game_and_collect_positions
from label import label_positions_with_stockfish
from train import train_nnue, burst_train, DEFAULT_HIDDEN_SIZES
from export import export_to_nbai
from tactical_positions_extractor import (
    download_and_extract_puzzle_db,
    extract_tactical_only
)
from lichess_importer import import_lichess_evals
from dataset import (
    get_datasets_dir,
    list_datasets,
    next_dataset_version,
    load_dataset_metadata,
    create_dataset,
    extend_dataset,
    get_dataset_labeled_path,
    delete_dataset,
    show_datasets_table
)


def get_weights_dir():
    """Get/create weights directory."""
    weights_dir = Path(__file__).parent / "weights"
    weights_dir.mkdir(exist_ok=True)
    return weights_dir


def get_data_dir():
    """Get/create legacy data directory (for migration)."""
    data_dir = Path(__file__).parent / "data"
    data_dir.mkdir(exist_ok=True)
    return data_dir


def list_checkpoints():
    """List available checkpoint versions."""
    weights_dir = get_weights_dir()
    checkpoints = sorted(weights_dir.glob("nnue_weights_v*.pt"))
    if not checkpoints:
        return []
    return [int(cp.stem.split("_v")[1]) for cp in checkpoints]


def migrate_legacy_data():
    """On first run, offer to import existing data/training_data.jsonl as ds_v1."""
    console = Console()
    data_dir = get_data_dir()
    legacy_file = data_dir / "training_data.jsonl"
    datasets = list_datasets()

    # Only migrate if legacy data exists and no datasets exist yet
    if legacy_file.exists() and not datasets:
        console.print("\n[cyan]Legacy data detected: data/training_data.jsonl[/cyan]")
        console.print("[dim]Tip: Use 'Manage Training Data' menu to import it as ds_v1[/dim]")


def show_header():
    """Display application header."""
    console = Console()
    console.clear()
    console.print(
        Panel(
            "[bold cyan]🧠 NNUE Training Pipeline[/bold cyan]\n"
            "[dim]Neural Network Utility Evaluation - Dataset & Model Management[/dim]",
            border_style="cyan",
            padding=(1, 2),
        )
    )


def show_checkpoints_table():
    """Display available checkpoints in a table."""
    console = Console()
    available = list_checkpoints()

    if not available:
        console.print("[yellow]ℹ No model checkpoints found yet[/yellow]")
        return

    table = Table(title="Available Model Checkpoints", show_header=True, header_style="bold cyan")
    table.add_column("Version", style="dim")
    table.add_column("File Size", justify="right")
    table.add_column("Status", justify="center")

    weights_dir = get_weights_dir()
    for v in sorted(available):
        weights_file = weights_dir / f"nnue_weights_v{v}.pt"
        if weights_file.exists():
            size = weights_file.stat().st_size / (1024**2)
            table.add_row(f"v{v}", f"{size:.1f} MB", "✓ Ready")
        else:
            table.add_row(f"v{v}", "?", "[red]✗ Missing[/red]")

    console.print(table)


def show_main_menu():
    """Display and handle main menu."""
    console = Console()

    # Migrate legacy data on first run
    migrate_legacy_data()

    while True:
        show_header()
        show_checkpoints_table()

        console.print("\n[bold]What would you like to do?[/bold]")
        console.print("[cyan]1[/cyan] - Manage Training Data")
        console.print("[cyan]2[/cyan] - Train Model")
        console.print("[cyan]3[/cyan] - Export Model")
        console.print("[cyan]4[/cyan] - Exit")

        choice = Prompt.ask("\nSelect option", choices=["1", "2", "3", "4"])

        if choice == "1":
            datasets_menu()
        elif choice == "2":
            training_menu()
        elif choice == "3":
            export_interactive()
        elif choice == "4":
            console.print("[yellow]👋 Goodbye![/yellow]")
            return


def datasets_menu():
    """Dataset management submenu."""
    console = Console()

    while True:
        show_header()
        show_datasets_table(console)

        console.print("\n[bold]Training Data Management[/bold]")
        console.print("[cyan]1[/cyan] - Create new dataset")
        console.print("[cyan]2[/cyan] - Extend existing dataset")
        console.print("[cyan]3[/cyan] - View all datasets")
        console.print("[cyan]4[/cyan] - Delete dataset")
        console.print("[cyan]5[/cyan] - Back")

        choice = Prompt.ask("\nSelect option", choices=["1", "2", "3", "4", "5"])

        if choice == "1":
            create_dataset_interactive()
        elif choice == "2":
            extend_dataset_interactive()
        elif choice == "3":
            show_header()
            show_datasets_table(console)
            Prompt.ask("\nPress Enter to continue")
        elif choice == "4":
            delete_dataset_interactive()
        elif choice == "5":
            return


def create_dataset_interactive():
    """Interactive dataset creation flow."""
    console = Console()
    show_header()

    console.print("\n[bold cyan]📊 Create New Dataset[/bold cyan]")

    sources = []
    combined_count = 0

    # Allow user to add multiple sources
    while True:
        console.print("\n[bold]Add data source (repeat until done):[/bold]")
        console.print("[cyan]a[/cyan] - Generate random positions")
        console.print("[cyan]b[/cyan] - Import from file")
        console.print("[cyan]c[/cyan] - Extract Lichess tactical puzzles")
        console.print("[cyan]d[/cyan] - Import Lichess eval database (.jsonl.zst)")
        console.print("[cyan]e[/cyan] - Done adding sources")

        choice = Prompt.ask("Select", choices=["a", "b", "c", "d", "e"])

        if choice == "a":
            num_positions = int(Prompt.ask("Number of positions to generate", default="100000"))
            min_move = int(Prompt.ask("Minimum move number", default="1"))
            max_move = int(Prompt.ask("Maximum move number", default="50"))
            num_workers = int(Prompt.ask("Number of workers", default="8"))

            console.print("[dim]Generating positions...[/dim]")
            temp_file = Path(tempfile.gettempdir()) / "temp_positions.txt"
            count = play_random_game_and_collect_positions(
                str(temp_file),
                total_positions=num_positions,
                samples_per_game=1,
                min_move=min_move,
                max_move=max_move,
                num_workers=num_workers
            )
            if count > 0:
                sources.append({
                    "type": "generated",
                    "count": count,
                    "params": {"num_positions": num_positions, "min_move": min_move, "max_move": max_move}
                })
                combined_count += count
                console.print(f"[green]✓ {count:,} positions generated[/green]")
            else:
                console.print("[red]✗ Generation failed[/red]")

        elif choice == "b":
            file_path = Prompt.ask("Path to FEN file")
            try:
                with open(file_path, 'r') as f:
                    count = sum(1 for _ in f)
                sources.append({"type": "file_import", "count": count, "path": file_path})
                combined_count += count
                console.print(f"[green]✓ {count:,} positions from file[/green]")
            except FileNotFoundError:
                console.print(f"[red]✗ File not found: {file_path}[/red]")

        elif choice == "c":
            max_puzzles = int(Prompt.ask("Maximum puzzles to extract", default="300000"))
            console.print("[dim]Extracting tactical positions...[/dim]")
            temp_file = Path(tempfile.gettempdir()) / "temp_tactical.txt"
            try:
                csv_path = download_and_extract_puzzle_db(output_dir=str(Path(__file__).parent / "tactical_data"))
                if csv_path:
                    count = extract_tactical_only(csv_path, str(temp_file), max_puzzles)
                    sources.append({"type": "tactical", "count": count, "max_puzzles": max_puzzles})
                    combined_count += count
                    console.print(f"[green]✓ {count:,} tactical positions extracted[/green]")
            except Exception as e:
                console.print(f"[red]✗ Tactical extraction failed: {e}[/red]")

        elif choice == "d":
            zst_path = Prompt.ask("Path to lichess_db_eval.jsonl.zst")
            max_pos = Prompt.ask("Max positions to import (blank = no limit)", default="")
            max_pos = int(max_pos) if max_pos.strip() else None
            min_depth = int(Prompt.ask("Minimum eval depth to accept", default="20"))
            console.print("[dim]Importing Lichess evals (this may take a while)...[/dim]")
            temp_file = Path(tempfile.gettempdir()) / "temp_lichess.jsonl"
            temp_file.unlink(missing_ok=True)
            try:
                count = import_lichess_evals(
                    input_path=zst_path,
                    output_file=str(temp_file),
                    max_positions=max_pos,
                    min_depth=min_depth,
                )
                if count > 0:
                    sources.append({
                        "type": "lichess",
                        "count": count,
                        "params": {"min_depth": min_depth, "max_positions": max_pos},
                    })
                    combined_count += count
                    console.print(f"[green]✓ {count:,} positions imported from Lichess[/green]")
                else:
                    console.print("[red]✗ No positions imported[/red]")
            except Exception as e:
                console.print(f"[red]✗ Lichess import failed: {e}[/red]")

        elif choice == "e":
            if not sources:
                console.print("[yellow]⚠ No sources added yet[/yellow]")
                continue
            break

    if not sources:
        console.print("[yellow]Dataset creation cancelled[/yellow]")
        return

    # Determine whether any sources still need Stockfish labeling.
    # Lichess sources are already labeled; only generated/tactical/file sources need it.
    needs_labeling = any(s["type"] != "lichess" for s in sources)

    stockfish_depth = 12
    if needs_labeling:
        console.print("\n[bold cyan]🏷️  Labeling Parameters[/bold cyan]")
        stockfish_path = Prompt.ask(
            "Stockfish path",
            default=os.environ.get("STOCKFISH_PATH") or shutil.which("stockfish") or "/usr/bin/stockfish"
        )
        stockfish_depth = int(Prompt.ask("Stockfish analysis depth", default="12"))
        num_workers = int(Prompt.ask("Number of parallel workers", default="1"))

    # Summary and confirm
    console.print("\n[bold]Dataset Summary:[/bold]")
    console.print(f"  Total positions: {combined_count:,}")
    for source in sources:
        console.print(f"  - {source['type']}: {source['count']:,}")
    if needs_labeling:
        console.print(f"  Stockfish depth: {stockfish_depth}")

    if not Confirm.ask("\nProceed to create dataset?", default=True):
        console.print("[yellow]Cancelled[/yellow]")
        return

    try:
        labeled_file = Path(tempfile.gettempdir()) / "labeled.jsonl"
        labeled_file.unlink(missing_ok=True)

        # --- Step 1: Collect already-labeled data (Lichess source) ---
        lichess_tmp = Path(tempfile.gettempdir()) / "temp_lichess.jsonl"
        if lichess_tmp.exists():
            import shutil as _shutil
            _shutil.copy(lichess_tmp, labeled_file)
            console.print(f"\n[bold cyan]Step 1: Pre-labeled data copied[/bold cyan]")
            console.print(f"[green]✓ Lichess positions ready[/green]")

        # --- Step 2: Combine unlabeled sources and run Stockfish (if any) ---
        non_lichess = [s for s in sources if s["type"] != "lichess"]
        if non_lichess:
            console.print("\n[bold cyan]Step 2: Combining unlabeled sources[/bold cyan]")
            combined_fen_file = Path(tempfile.gettempdir()) / "combined_positions.txt"
            all_fens = set()

            for source in non_lichess:
                if source["type"] == "generated":
                    temp_file = Path(tempfile.gettempdir()) / "temp_positions.txt"
                elif source["type"] == "file_import":
                    temp_file = Path(source["path"])
                elif source["type"] == "tactical":
                    temp_file = Path(tempfile.gettempdir()) / "temp_tactical.txt"
                else:
                    continue

                if temp_file.exists():
                    with open(temp_file, "r") as f:
                        for line in f:
                            fen = line.strip()
                            if fen:
                                all_fens.add(fen)

            with open(combined_fen_file, "w") as f:
                for fen in all_fens:
                    f.write(fen + "\n")
            console.print(f"[green]✓ Combined {len(all_fens):,} unique unlabeled positions[/green]")

            console.print("\n[bold cyan]Step 2b: Labeling with Stockfish[/bold cyan]")
            success = label_positions_with_stockfish(
                str(combined_fen_file),
                str(labeled_file),
                stockfish_path,
                depth=stockfish_depth,
                num_workers=num_workers,
            )
            if not success:
                console.print("[red]✗ Stockfish labeling failed[/red]")
                return
            console.print("[green]✓ Positions labeled[/green]")

        # --- Step 3: Create dataset ---
        console.print("\n[bold cyan]Step 3: Creating Dataset[/bold cyan]")
        version = next_dataset_version()
        create_dataset(
            version=version,
            labeled_jsonl_path=str(labeled_file),
            sources=sources,
            stockfish_depth=stockfish_depth,
        )
        console.print(f"[green]✓ Dataset created: ds_v{version}[/green]")
        console.print(f"[bold]Location: {get_datasets_dir() / f'ds_v{version}'}[/bold]")

        Prompt.ask("\nPress Enter to continue")

    except Exception as e:
        console.print(f"[red]✗ Error: {e}[/red]")
        import traceback
        traceback.print_exc()
        Prompt.ask("Press Enter to continue")


def extend_dataset_interactive():
    """Interactive dataset extension flow."""
    console = Console()
    show_header()

    console.print("\n[bold cyan]📊 Extend Existing Dataset[/bold cyan]")

    datasets = list_datasets()
    if not datasets:
        console.print("[yellow]ℹ No datasets available to extend[/yellow]")
        Prompt.ask("Press Enter to continue")
        return

    show_datasets_table(console)
    version = int(Prompt.ask("\nEnter dataset version to extend (e.g., 1)"))

    if not any(v == version for v, _ in datasets):
        console.print("[red]✗ Dataset not found[/red]")
        return

    sources = []
    combined_count = 0

    # Allow user to add sources
    while True:
        console.print("\n[bold]Add data source:[/bold]")
        console.print("[cyan]a[/cyan] - Generate random positions")
        console.print("[cyan]b[/cyan] - Import from file")
        console.print("[cyan]c[/cyan] - Extract Lichess tactical puzzles")
        console.print("[cyan]d[/cyan] - Import Lichess eval database (.jsonl.zst)")
        console.print("[cyan]e[/cyan] - Done adding sources")

        choice = Prompt.ask("Select", choices=["a", "b", "c", "d", "e"])

        if choice == "a":
            num_positions = int(Prompt.ask("Number of positions to generate", default="100000"))
            min_move = int(Prompt.ask("Minimum move number", default="1"))
            max_move = int(Prompt.ask("Maximum move number", default="50"))
            num_workers = int(Prompt.ask("Number of workers", default="8"))

            console.print("[dim]Generating positions...[/dim]")
            temp_file = Path(tempfile.gettempdir()) / "temp_positions.txt"
            count = play_random_game_and_collect_positions(
                str(temp_file),
                total_positions=num_positions,
                samples_per_game=1,
                min_move=min_move,
                max_move=max_move,
                num_workers=num_workers
            )
            if count > 0:
                sources.append({
                    "type": "generated",
                    "count": count,
                    "params": {"num_positions": num_positions, "min_move": min_move, "max_move": max_move}
                })
                combined_count += count
                console.print(f"[green]✓ {count:,} positions generated[/green]")

        elif choice == "b":
            file_path = Prompt.ask("Path to FEN file")
            try:
                with open(file_path, 'r') as f:
                    count = sum(1 for _ in f)
                sources.append({"type": "file_import", "count": count, "path": file_path})
                combined_count += count
                console.print(f"[green]✓ {count:,} positions from file[/green]")
            except FileNotFoundError:
                console.print(f"[red]✗ File not found: {file_path}[/red]")

        elif choice == "c":
            max_puzzles = int(Prompt.ask("Maximum puzzles to extract", default="300000"))
            console.print("[dim]Extracting tactical positions...[/dim]")
            temp_file = Path(tempfile.gettempdir()) / "temp_tactical.txt"
            try:
                csv_path = download_and_extract_puzzle_db(output_dir=str(Path(__file__).parent / "tactical_data"))
                if csv_path:
                    count = extract_tactical_only(csv_path, str(temp_file), max_puzzles)
                    sources.append({"type": "tactical", "count": count, "max_puzzles": max_puzzles})
                    combined_count += count
                    console.print(f"[green]✓ {count:,} tactical positions extracted[/green]")
            except Exception as e:
                console.print(f"[red]✗ Extraction failed: {e}[/red]")

        elif choice == "d":
            zst_path = Prompt.ask("Path to lichess_db_eval.jsonl.zst")
            max_pos = Prompt.ask("Max positions to import (blank = no limit)", default="")
            max_pos = int(max_pos) if max_pos.strip() else None
            min_depth = int(Prompt.ask("Minimum eval depth to accept", default="20"))
            console.print("[dim]Importing Lichess evals (this may take a while)...[/dim]")
            temp_file = Path(tempfile.gettempdir()) / "temp_lichess.jsonl"
            temp_file.unlink(missing_ok=True)
            try:
                count = import_lichess_evals(
                    input_path=zst_path,
                    output_file=str(temp_file),
                    max_positions=max_pos,
                    min_depth=min_depth,
                )
                if count > 0:
                    sources.append({
                        "type": "lichess",
                        "count": count,
                        "params": {"min_depth": min_depth, "max_positions": max_pos},
                    })
                    combined_count += count
                    console.print(f"[green]✓ {count:,} positions imported from Lichess[/green]")
                else:
                    console.print("[red]✗ No positions imported[/red]")
            except Exception as e:
                console.print(f"[red]✗ Lichess import failed: {e}[/red]")

        elif choice == "e":
            if not sources:
                console.print("[yellow]⚠ No sources added yet[/yellow]")
                continue
            break

    if not sources:
        console.print("[yellow]Extension cancelled[/yellow]")
        return

    needs_labeling = any(s["type"] != "lichess" for s in sources)

    stockfish_depth = 12
    if needs_labeling:
        console.print("\n[bold cyan]🏷️  Labeling Parameters[/bold cyan]")
        stockfish_path = Prompt.ask(
            "Stockfish path",
            default=os.environ.get("STOCKFISH_PATH") or shutil.which("stockfish") or "/usr/bin/stockfish"
        )
        stockfish_depth = int(Prompt.ask("Stockfish analysis depth", default="12"))
        num_workers = int(Prompt.ask("Number of parallel workers", default="1"))

    # Summary and confirm
    console.print("\n[bold]Extension Summary:[/bold]")
    console.print(f"  Target dataset: ds_v{version}")
    console.print(f"  New positions: {combined_count:,}")
    for source in sources:
        console.print(f"  - {source['type']}: {source['count']:,}")
    if needs_labeling:
        console.print(f"  Stockfish depth: {stockfish_depth}")

    if not Confirm.ask("\nProceed to extend dataset?", default=True):
        console.print("[yellow]Cancelled[/yellow]")
        return

    try:
        labeled_file = Path(tempfile.gettempdir()) / "labeled.jsonl"
        labeled_file.unlink(missing_ok=True)

        # Copy pre-labeled Lichess data if present
        lichess_tmp = Path(tempfile.gettempdir()) / "temp_lichess.jsonl"
        if lichess_tmp.exists():
            import shutil as _shutil
            _shutil.copy(lichess_tmp, labeled_file)
            console.print(f"\n[bold cyan]Step 1: Pre-labeled data copied[/bold cyan]")
            console.print(f"[green]✓ Lichess positions ready[/green]")

        # Combine and label remaining sources with Stockfish
        non_lichess = [s for s in sources if s["type"] != "lichess"]
        if non_lichess:
            console.print("\n[bold cyan]Step 2: Combining unlabeled sources[/bold cyan]")
            combined_fen_file = Path(tempfile.gettempdir()) / "combined_positions.txt"
            all_fens = set()

            for source in non_lichess:
                if source["type"] == "generated":
                    temp_file = Path(tempfile.gettempdir()) / "temp_positions.txt"
                elif source["type"] == "file_import":
                    temp_file = Path(source["path"])
                elif source["type"] == "tactical":
                    temp_file = Path(tempfile.gettempdir()) / "temp_tactical.txt"
                else:
                    continue
                if temp_file.exists():
                    with open(temp_file, "r") as f:
                        for line in f:
                            fen = line.strip()
                            if fen:
                                all_fens.add(fen)

            with open(combined_fen_file, "w") as f:
                for fen in all_fens:
                    f.write(fen + "\n")
            console.print(f"[green]✓ Combined {len(all_fens):,} unique unlabeled positions[/green]")

            console.print("\n[bold cyan]Step 2b: Labeling with Stockfish[/bold cyan]")
            success = label_positions_with_stockfish(
                str(combined_fen_file),
                str(labeled_file),
                stockfish_path,
                depth=stockfish_depth,
                num_workers=num_workers,
            )
            if not success:
                console.print("[red]✗ Stockfish labeling failed[/red]")
                return
            console.print("[green]✓ Positions labeled[/green]")

        # Extend dataset
        console.print("\n[bold cyan]Step 3: Extending Dataset[/bold cyan]")
        success = extend_dataset(
            version=version,
            new_labeled_path=str(labeled_file),
            new_source_entry={
                "type": "merged_sources",
                "count": combined_count,
                "sources": sources,
            }
        )

        if success:
            metadata = load_dataset_metadata(version)
            console.print(f"[green]✓ Dataset extended[/green]")
            console.print(f"[bold]Total positions: {metadata['total_positions']:,}[/bold]")
        else:
            console.print("[red]✗ Extension failed[/red]")

        Prompt.ask("\nPress Enter to continue")

    except Exception as e:
        console.print(f"[red]✗ Error: {e}[/red]")
        import traceback
        traceback.print_exc()
        Prompt.ask("Press Enter to continue")


def delete_dataset_interactive():
    """Interactive dataset deletion."""
    console = Console()
    show_header()

    console.print("\n[bold cyan]⚠️  Delete Dataset[/bold cyan]")

    datasets = list_datasets()
    if not datasets:
        console.print("[yellow]ℹ No datasets to delete[/yellow]")
        Prompt.ask("Press Enter to continue")
        return

    show_datasets_table(console)
    version = int(Prompt.ask("\nEnter dataset version to delete (e.g., 1)"))

    if not any(v == version for v, _ in datasets):
        console.print("[red]✗ Dataset not found[/red]")
        return

    if Confirm.ask(f"Delete ds_v{version}? This cannot be undone.", default=False):
        if delete_dataset(version):
            console.print(f"[green]✓ Dataset ds_v{version} deleted[/green]")
        else:
            console.print("[red]✗ Deletion failed[/red]")

    Prompt.ask("Press Enter to continue")


def training_menu():
    """Training submenu."""
    console = Console()

    while True:
        show_header()

        console.print("\n[bold]Training[/bold]")
        console.print("[cyan]1[/cyan] - Standard Training")
        console.print("[cyan]2[/cyan] - Burst Training")
        console.print("[cyan]3[/cyan] - View Model Checkpoints")
        console.print("[cyan]4[/cyan] - Back")

        choice = Prompt.ask("\nSelect option", choices=["1", "2", "3", "4"])

        if choice == "1":
            train_interactive()
        elif choice == "2":
            burst_train_interactive()
        elif choice == "3":
            show_header()
            show_checkpoints_table()
            Prompt.ask("\nPress Enter to continue")
        elif choice == "4":
            return


def train_interactive():
    """Interactive training menu."""
    console = Console()
    show_header()

    console.print("\n[bold cyan]📚 Standard Training Configuration[/bold cyan]")

    # Dataset selection
    datasets = list_datasets()
    if not datasets:
        console.print("[red]✗ No datasets available. Create one first.[/red]")
        Prompt.ask("Press Enter to continue")
        return

    console.print("\n[bold]Available Datasets:[/bold]")
    show_datasets_table(console)
    dataset_version = int(Prompt.ask("\nEnter dataset version to train on (e.g., 1)"))

    if not any(v == dataset_version for v, _ in datasets):
        console.print("[red]✗ Dataset not found[/red]")
        return

    labeled_file = get_dataset_labeled_path(dataset_version)
    if not labeled_file:
        console.print("[red]✗ Dataset labeled.jsonl not found[/red]")
        return

    # Checkpoint selection
    available = list_checkpoints()
    use_checkpoint = False
    checkpoint_version = None

    if available:
        console.print(f"\n[dim]Available checkpoints: {', '.join([f'v{v}' for v in sorted(available)])}[/dim]")
        use_checkpoint = Confirm.ask("Start from an existing checkpoint?", default=False)
        if use_checkpoint:
            checkpoint_version = Prompt.ask(
                "Enter checkpoint version",
                default=str(max(available))
            )

    # Training parameters
    epochs = int(Prompt.ask("Number of epochs", default="100"))
    batch_size = int(Prompt.ask("Batch size", default="16384"))
    subsample_ratio = float(Prompt.ask("Stochastic subsample ratio per epoch (1.0 = all data)", default="1.0"))
    default_layers = ",".join(str(s) for s in DEFAULT_HIDDEN_SIZES)
    hidden_layers_str = Prompt.ask(
        "Hidden layer sizes (comma-separated, e.g. 1536,1024,512,256)",
        default=default_layers
    )
    hidden_sizes = [int(x.strip()) for x in hidden_layers_str.split(",") if x.strip()]
    early_stopping = None
    if Confirm.ask("Enable early stopping?", default=False):
        early_stopping = int(Prompt.ask("Patience (epochs)", default="5"))

    arch_str = " → ".join(str(s) for s in [768] + hidden_sizes + [1])

    # Confirm and start
    console.print("\n[bold]Configuration Summary:[/bold]")
    console.print(f"  Dataset: ds_v{dataset_version}")
    console.print(f"  Architecture: {arch_str}")
    console.print(f"  Epochs: {epochs}")
    console.print(f"  Batch size: {batch_size}")
    console.print(f"  Subsample ratio: {subsample_ratio:.0%}")
    if early_stopping:
        console.print(f"  Early stopping: Yes (patience: {early_stopping})")
    else:
        console.print(f"  Early stopping: No")
    if use_checkpoint:
        console.print(f"  Checkpoint: v{checkpoint_version}")
    else:
        console.print(f"  Checkpoint: None (training from scratch)")

    if not Confirm.ask("\nStart training?", default=True):
        console.print("[yellow]Training cancelled[/yellow]")
        Prompt.ask("Press Enter to continue")
        return

    # Execute training
    weights_dir = get_weights_dir()

    try:
        console.print("\n[bold cyan]Training Model[/bold cyan]")
        checkpoint = None
        if use_checkpoint:
            checkpoint = str(weights_dir / f"nnue_weights_v{checkpoint_version}.pt")

        train_nnue(
            data_file=str(labeled_file),
            output_file=str(weights_dir / "nnue_weights.pt"),
            epochs=epochs,
            batch_size=batch_size,
            checkpoint=checkpoint,
            use_versioning=True,
            early_stopping_patience=early_stopping,
            subsample_ratio=subsample_ratio,
            hidden_sizes=hidden_sizes,
        )
        console.print("[green]✓ Training complete[/green]")

        # Show result
        available = list_checkpoints()
        new_version = max(available) if available else 1
        console.print(f"\n[bold green]✓ Training successful![/bold green]")
        console.print(f"[bold]New checkpoint: v{new_version}[/bold]")
        Prompt.ask("Press Enter to continue")

    except Exception as e:
        console.print(f"[red]✗ Error: {e}[/red]")
        import traceback
        traceback.print_exc()
        Prompt.ask("Press Enter to continue")


def burst_train_interactive():
    """Interactive burst training menu."""
    console = Console()
    show_header()

    console.print("\n[bold cyan]⚡ Burst Training Configuration[/bold cyan]")
    console.print("[dim]Repeatedly restarts from the best checkpoint until the time budget expires.[/dim]\n")

    # Dataset selection
    datasets = list_datasets()
    if not datasets:
        console.print("[red]✗ No datasets available. Create one first.[/red]")
        Prompt.ask("Press Enter to continue")
        return

    console.print("[bold]Available Datasets:[/bold]")
    show_datasets_table(console)
    dataset_version = int(Prompt.ask("\nEnter dataset version to train on (e.g., 1)"))

    if not any(v == dataset_version for v, _ in datasets):
        console.print("[red]✗ Dataset not found[/red]")
        return

    labeled_file = get_dataset_labeled_path(dataset_version)
    if not labeled_file:
        console.print("[red]✗ Dataset labeled.jsonl not found[/red]")
        return

    duration_minutes = float(Prompt.ask("Training budget (minutes)", default="60"))
    epochs_per_season = int(Prompt.ask("Max epochs per season", default="50"))
    early_stopping_patience = int(Prompt.ask("Early stopping patience (epochs)", default="10"))

    # Optional initial checkpoint
    available = list_checkpoints()
    checkpoint = None
    if available:
        console.print(f"\n[dim]Available checkpoints: {', '.join([f'v{v}' for v in sorted(available)])}[/dim]")
        if Confirm.ask("Start from an existing checkpoint?", default=False):
            version = Prompt.ask("Enter checkpoint version", default=str(max(available)))
            checkpoint = str(get_weights_dir() / f"nnue_weights_v{version}.pt")

    # Training hyperparameters
    batch_size = int(Prompt.ask("Batch size", default="16384"))
    subsample_ratio = float(Prompt.ask("Stochastic subsample ratio per epoch (1.0 = all data)", default="1.0"))
    default_layers = ",".join(str(s) for s in DEFAULT_HIDDEN_SIZES)
    hidden_layers_str = Prompt.ask(
        "Hidden layer sizes (comma-separated, e.g. 1536,1024,512,256)",
        default=default_layers
    )
    hidden_sizes = [int(x.strip()) for x in hidden_layers_str.split(",") if x.strip()]
    arch_str = " → ".join(str(s) for s in [768] + hidden_sizes + [1])

    # Summary
    console.print("\n[bold]Configuration Summary:[/bold]")
    console.print(f"  Dataset:             ds_v{dataset_version}")
    console.print(f"  Architecture:        {arch_str}")
    console.print(f"  Duration:            {duration_minutes:.0f} minutes")
    console.print(f"  Epochs per season:   {epochs_per_season}")
    console.print(f"  Patience:            {early_stopping_patience}")
    console.print(f"  Batch size:          {batch_size}")
    console.print(f"  Subsample ratio:     {subsample_ratio:.0%}")
    console.print(f"  Checkpoint:          {checkpoint or 'None (from scratch)'}")

    if not Confirm.ask("\nStart burst training?", default=True):
        console.print("[yellow]Burst training cancelled[/yellow]")
        Prompt.ask("Press Enter to continue")
        return

    weights_dir = get_weights_dir()

    try:
        console.print("\n[bold cyan]Burst Training[/bold cyan]")
        burst_train(
            data_file=str(labeled_file),
            output_file=str(weights_dir / "nnue_weights.pt"),
            duration_minutes=duration_minutes,
            epochs_per_season=epochs_per_season,
            early_stopping_patience=early_stopping_patience,
            batch_size=batch_size,
            initial_checkpoint=checkpoint,
            use_versioning=True,
            subsample_ratio=subsample_ratio,
            hidden_sizes=hidden_sizes,
        )
        console.print("[green]✓ Burst training complete[/green]")

        available = list_checkpoints()
        if available:
            console.print(f"[bold]Latest checkpoint: v{max(available)}[/bold]")
        Prompt.ask("Press Enter to continue")

    except Exception as e:
        console.print(f"[red]✗ Error: {e}[/red]")
        import traceback
        traceback.print_exc()
        Prompt.ask("Press Enter to continue")


def export_interactive():
    """Interactive export menu."""
    console = Console()
    show_header()

    console.print("\n[bold cyan]📦 Export Configuration[/bold cyan]")

    # Select weights version
    available = list_checkpoints()
    if not available:
        console.print("[red]✗ No checkpoints available to export[/red]")
        Prompt.ask("Press Enter to continue")
        return

    console.print(f"[dim]Available versions: {', '.join([f'v{v}' for v in sorted(available)])}[/dim]")
    version = Prompt.ask("Enter version to export (e.g., 2)")

    weights_file = f"nnue_weights_v{version}.pt"
    output_file = str(Path(__file__).parent.parent / "src" / "main" / "resources" / "nnue_weights.nbai")

    console.print(f"\n[bold]Export Configuration:[/bold]")
    console.print(f"  Source: {weights_file}")
    console.print(f"  Destination: {output_file}")

    if not Confirm.ask("\nExport weights?", default=True):
        console.print("[yellow]Export cancelled[/yellow]")
        return

    try:
        weights_dir = get_weights_dir()
        weights_path = weights_dir / weights_file

        if not weights_path.exists():
            console.print(f"[red]✗ {weights_file} not found[/red]")
            return

        console.print("\n[bold cyan]Exporting Weights[/bold cyan]")
        export_to_nbai(str(weights_path), output_file)
        console.print(f"\n[green]✓ Export complete![/green]")
        console.print(f"[bold]Weights saved to:[/bold] {output_file}")
        Prompt.ask("Press Enter to continue")

    except Exception as e:
        console.print(f"[red]✗ Error: {e}[/red]")
        import traceback
        traceback.print_exc()
        Prompt.ask("Press Enter to continue")


def main():
    try:
        show_main_menu()
        return 0
    except KeyboardInterrupt:
        console = Console()
        console.print("\n[yellow]Interrupted by user[/yellow]")
        return 1
    except Exception as e:
        console = Console()
        console.print(f"[red]Error:[/red] {e}")
        import traceback
        traceback.print_exc()
        return 1


if __name__ == "__main__":
    sys.exit(main())
