#!/usr/bin/env python3
"""Train NNUE neural network for chess evaluation."""

import json
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, Dataset
import sys
from pathlib import Path
from tqdm import tqdm
import chess
from datetime import datetime, timedelta
import re
import numpy as np

class NNUEDataset(Dataset):
    """Dataset of chess positions with evaluations."""

    def __init__(self, data_file):
        self.positions = []
        self.evals = []
        self.evals_raw = []
        self.is_normalized = None

        with open(data_file, 'r') as f:
            for line in f:
                try:
                    data = json.loads(line)
                    fen = data['fen']
                    eval_val = data['eval']
                    self.positions.append(fen)
                    self.evals.append(eval_val)

                    # Check if normalized or raw
                    if self.is_normalized is None:
                        # If eval is in range [-1, 1], assume normalized
                        self.is_normalized = abs(eval_val) <= 1.0

                    # Store raw if available
                    if 'eval_raw' in data:
                        self.evals_raw.append(data['eval_raw'])
                    else:
                        self.evals_raw.append(eval_val)
                except (json.JSONDecodeError, KeyError):
                    pass

    def __len__(self):
        return len(self.positions)

    def __getitem__(self, idx):
        fen = self.positions[idx]
        eval_val = self.evals[idx]
        features = fen_to_features(fen)

        # Use evaluation as-is if normalized, otherwise apply sigmoid scaling
        if self.is_normalized:
            target = torch.tensor(eval_val, dtype=torch.float32)
        else:
            target = torch.sigmoid(torch.tensor(eval_val / 400.0, dtype=torch.float32))

        return features, target

def fen_to_features(fen):
    """Convert FEN to 768-dimensional binary feature vector."""
    # Piece type to index: pawn=0, knight=1, bishop=2, rook=3, queen=4, king=5
    piece_to_idx = {'p': 0, 'n': 1, 'b': 2, 'r': 3, 'q': 4, 'k': 5,
                    'P': 6, 'N': 7, 'B': 8, 'R': 9, 'Q': 10, 'K': 11}

    features = torch.zeros(768, dtype=torch.float32)

    try:
        board = chess.Board(fen)

        # 12 piece types × 64 squares = 768
        for square in chess.SQUARES:
            piece = board.piece_at(square)
            if piece is not None:
                piece_char = piece.symbol()
                if piece_char in piece_to_idx:
                    piece_idx = piece_to_idx[piece_char]
                    feature_idx = piece_idx * 64 + square
                    features[feature_idx] = 1.0
    except:
        pass

    return features

DEFAULT_HIDDEN_SIZES = [1536, 1024, 512, 256]


class NNUE(nn.Module):
    """NNUE neural network with configurable hidden layers.

    Architecture: 768 → hidden_sizes[0] → ... → hidden_sizes[-1] → 1
    Layer attributes follow the naming l1, l2, ..., lN so export.py can
    infer the architecture directly from the state_dict.
    """

    def __init__(self, hidden_sizes=None, dropout_rate=0.2):
        super().__init__()
        if hidden_sizes is None:
            hidden_sizes = DEFAULT_HIDDEN_SIZES
        self.hidden_sizes = list(hidden_sizes)
        sizes = [768] + self.hidden_sizes + [1]
        num_hidden = len(self.hidden_sizes)

        for i in range(num_hidden):
            setattr(self, f"l{i + 1}", nn.Linear(sizes[i], sizes[i + 1]))
            setattr(self, f"relu{i + 1}", nn.ReLU())
            setattr(self, f"drop{i + 1}", nn.Dropout(dropout_rate))
        setattr(self, f"l{num_hidden + 1}", nn.Linear(sizes[-2], sizes[-1]))
        self._num_hidden = num_hidden

    def forward(self, x):
        for i in range(1, self._num_hidden + 1):
            layer = getattr(self, f"l{i}")
            relu = getattr(self, f"relu{i}")
            drop = getattr(self, f"drop{i}")
            x = drop(relu(layer(x)))
        return getattr(self, f"l{self._num_hidden + 1}")(x)

def find_next_version(base_name="nnue_weights"):
    """Find the next version number for model versioning.

    Looks for nnue_weights_v*.pt files and returns the next version number.
    If no versioned files exist, returns 1.
    """
    base_path = Path(base_name)
    directory = base_path.parent
    filename = base_path.name

    pattern = re.compile(rf"{re.escape(filename)}_v(\d+)\.pt")
    versions = []

    for file in directory.glob(f"{filename}_v*.pt"):
        match = pattern.match(file.name)
        if match:
            versions.append(int(match.group(1)))

    if versions:
        return max(versions) + 1
    return 1

def save_metadata(weights_file, metadata):
    """Save training metadata alongside the weights file.

    Args:
        weights_file: Path to the .pt file (e.g., nnue_weights_v1.pt)
        metadata: Dictionary with training info
    """
    metadata_file = weights_file.replace(".pt", "_metadata.json")

    with open(metadata_file, "w") as f:
        json.dump(metadata, f, indent=2, default=str)

    return metadata_file

def _setup_training(data_file, batch_size, subsample_ratio):
    """Set up device, dataset, and data loaders.

    Returns:
        (device, dataset, train_dataset, val_dataset, train_loader, val_loader, num_positions)
    """
    print("Checking GPU availability...")
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    if torch.cuda.is_available():
        print(f"✓ GPU available: {torch.cuda.get_device_name(0)}")
        print(f"  GPU memory: {torch.cuda.get_device_properties(0).total_memory / 1e9:.2f} GB")
    else:
        print("⚠ GPU not available, using CPU")
    print(f"Using device: {device}")
    print()

    print("Loading dataset...")
    dataset = NNUEDataset(data_file)
    num_positions = len(dataset)
    print(f"Dataset size: {num_positions}")
    print(f"Data normalization: {'Yes (tanh)' if dataset.is_normalized else 'No (raw centipawns)'})")

    evals_array = np.array(dataset.evals)
    print()
    print("=" * 60)
    print("TRAINING DATASET DIAGNOSTICS")
    print("=" * 60)
    print(f"Min evaluation:     {evals_array.min():.4f}")
    print(f"Max evaluation:     {evals_array.max():.4f}")
    print(f"Mean evaluation:    {evals_array.mean():.4f}")
    print(f"Median evaluation:  {np.median(evals_array):.4f}")
    print(f"Std deviation:      {evals_array.std():.4f}")
    print("=" * 60)
    print()

    train_size = int(0.9 * len(dataset))
    val_size = len(dataset) - train_size

    from torch.utils.data import random_split, RandomSampler
    generator = torch.Generator().manual_seed(42)
    train_dataset, val_dataset = random_split(dataset, [train_size, val_size], generator=generator)

    subsample_size = max(1, int(subsample_ratio * len(train_dataset)))
    train_sampler = RandomSampler(train_dataset, replacement=False, num_samples=subsample_size)
    train_loader = DataLoader(
        train_dataset,
        batch_size=batch_size,
        sampler=train_sampler,
        num_workers=8,
        pin_memory=True,
        persistent_workers=True
    )
    val_loader = DataLoader(
        val_dataset,
        batch_size=batch_size,
        shuffle=False,
        num_workers=8,
        pin_memory=True,
        persistent_workers=True
    )

    return device, dataset, train_dataset, val_dataset, train_loader, val_loader, num_positions

def _run_training_season(
    model, optimizer, scheduler, scaler,
    train_loader, val_loader, train_dataset, val_dataset,
    device, criterion, output_file,
    start_epoch, epochs, early_stopping_patience,
    season_start_time, deadline=None, initial_best_val_loss=float('inf')
):
    """Run one training season until epoch limit, early stopping, or deadline.

    Args:
        initial_best_val_loss: Baseline to beat — epochs that don't improve on this count
                               toward early stopping and do not save snapshots.
    Returns:
        (best_val_loss, best_model_state, last_epoch)
        best_model_state is None if no epoch beat initial_best_val_loss.
    """
    best_val_loss = initial_best_val_loss
    best_model_state = None
    epochs_without_improvement = 0
    total_epochs = start_epoch + epochs
    last_epoch = start_epoch - 1

    for epoch in range(start_epoch, start_epoch + epochs):
        if deadline and datetime.now() >= deadline:
            print("Time limit reached, stopping season.")
            break

        epoch_display = epoch + 1

        # Train
        model.train()
        train_loss = 0.0
        with tqdm(total=len(train_loader), desc=f"Epoch {epoch_display}/{total_epochs} - Train") as pbar:
            for batch_features, batch_targets in train_loader:
                batch_features = batch_features.to(device)
                batch_targets = batch_targets.to(device).unsqueeze(1)

                optimizer.zero_grad()

                with torch.amp.autocast('cuda' if torch.cuda.is_available() else 'cpu'):
                    outputs = model(batch_features)
                    loss = criterion(outputs, batch_targets)

                scaler.scale(loss).backward()
                scaler.step(optimizer)
                scaler.update()

                train_loss += loss.item() * batch_features.size(0)
                pbar.update(1)

        train_loss /= len(train_dataset)

        # Validation
        model.eval()
        val_loss = 0.0
        with torch.no_grad():
            with tqdm(total=len(val_loader), desc=f"Epoch {epoch_display}/{total_epochs} - Val") as pbar:
                for batch_features, batch_targets in val_loader:
                    batch_features = batch_features.to(device)
                    batch_targets = batch_targets.to(device).unsqueeze(1)

                    with torch.amp.autocast('cuda' if torch.cuda.is_available() else 'cpu'):
                        outputs = model(batch_features)
                        loss = criterion(outputs, batch_targets)
                    val_loss += loss.item() * batch_features.size(0)
                    pbar.update(1)

        val_loss /= len(val_dataset)

        scheduler.step()

        if torch.cuda.is_available():
            gpu_mem_used = torch.cuda.memory_allocated(device) / 1e9
            gpu_mem_reserved = torch.cuda.memory_reserved(device) / 1e9
            print(f"GPU Memory: {gpu_mem_used:.2f}GB used, {gpu_mem_reserved:.2f}GB reserved")

        elapsed_time = datetime.now() - season_start_time
        time_per_epoch = elapsed_time.total_seconds() / (epoch + 1)
        remaining_epochs = total_epochs - epoch_display
        eta_seconds = time_per_epoch * remaining_epochs
        eta_str = str(datetime.fromtimestamp(eta_seconds) - datetime.fromtimestamp(0)).split('.')[0]
        print(f"Epoch {epoch_display}: Train Loss = {train_loss:.6f}, Val Loss = {val_loss:.6f} | ETA: {eta_str}")

        checkpoint_file = output_file.replace(".pt", "_checkpoint.pt")
        torch.save({
            "epoch": epoch,
            "model_state_dict": model.state_dict(),
            "optimizer_state_dict": optimizer.state_dict(),
            "scheduler_state_dict": scheduler.state_dict(),
            "scaler_state_dict": scaler.state_dict(),
            "best_val_loss": best_val_loss,
            "hidden_sizes": model.hidden_sizes,
        }, checkpoint_file)

        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_model_state = model.state_dict().copy()
            epochs_without_improvement = 0
            snapshot_file = output_file.replace(".pt", "_best_snapshot.pt")
            torch.save(best_model_state, snapshot_file)
            print(f"  Best model snapshot saved: {snapshot_file} (val_loss: {val_loss:.6f})")
        else:
            epochs_without_improvement += 1

        last_epoch = epoch

        if early_stopping_patience and epochs_without_improvement >= early_stopping_patience:
            print(f"Early stopping: no improvement for {early_stopping_patience} epochs")
            break

    return best_val_loss, best_model_state, last_epoch

def _save_versioned_model(best_model_state, optimizer, scheduler, scaler, last_epoch,
                           best_val_loss, output_file, use_versioning, num_positions,
                           stockfish_depth, training_start_time, hidden_sizes=None,
                           extra_metadata=None):
    """Save the best model with optional versioning and metadata."""
    final_output_file = output_file
    metadata = {}
    architecture = [768] + list(hidden_sizes or DEFAULT_HIDDEN_SIZES) + [1]

    if use_versioning:
        base_name = output_file.replace(".pt", "")
        version = find_next_version(base_name)
        final_output_file = f"{base_name}_v{version}.pt"

        metadata = {
            "version": version,
            "date": training_start_time.isoformat(),
            "num_positions": num_positions,
            "stockfish_depth": stockfish_depth,
            "final_val_loss": float(best_val_loss),
            "architecture": architecture,
            "device": str(torch.device("cuda" if torch.cuda.is_available() else "cpu")),
            "notes": "Win rate vs classical eval: TBD (requires benchmark games)"
        }
        if extra_metadata:
            metadata.update(extra_metadata)

    torch.save({
        "model_state_dict": best_model_state,
        "optimizer_state_dict": optimizer.state_dict(),
        "scheduler_state_dict": scheduler.state_dict(),
        "scaler_state_dict": scaler.state_dict(),
        "epoch": last_epoch,
        "best_val_loss": best_val_loss,
        "hidden_sizes": list(hidden_sizes or DEFAULT_HIDDEN_SIZES),
    }, final_output_file)
    print(f"Best model saved to {final_output_file}")

    if use_versioning and metadata:
        metadata_file = save_metadata(final_output_file, metadata)
        print(f"Metadata saved to {metadata_file}")
        print(f"\nTraining Summary:")
        for key, val in metadata.items():
            print(f"  {key}: {val}")

def train_nnue(data_file, output_file="nnue_weights.pt", epochs=100, batch_size=16384, lr=0.001, checkpoint=None, stockfish_depth=12, use_versioning=True, early_stopping_patience=None, weight_decay=1e-4, subsample_ratio=1.0, hidden_sizes=None):
    """Train the NNUE model with GPU optimizations and automatic mixed precision.

    Args:
        data_file: Path to training_data.jsonl
        output_file: Where to save best weights (or base name if use_versioning=True)
        epochs: Number of training epochs (default: 100)
        batch_size: Training batch size (default: 16384)
        lr: Learning rate (default: 0.001)
        checkpoint: Optional path to checkpoint file to resume from
        stockfish_depth: Depth used in Stockfish evaluation (for metadata)
        use_versioning: If True, save as nnue_weights_v{N}.pt with metadata
        early_stopping_patience: Stop if val loss doesn't improve for N epochs (None to disable)
        weight_decay: L2 regularization strength (default: 1e-4, helps prevent overfitting)
        subsample_ratio: Fraction of training data to sample per epoch (default: 1.0 = all data)
        hidden_sizes: Hidden layer sizes (default: [1536, 1024, 512, 256])
    """
    device, dataset, train_dataset, val_dataset, train_loader, val_loader, num_positions = \
        _setup_training(data_file, batch_size, subsample_ratio)

    start_epoch = 0
    best_val_loss = float('inf')
    resolved_hidden_sizes = list(hidden_sizes or DEFAULT_HIDDEN_SIZES)

    if checkpoint:
        print(f"Loading checkpoint: {checkpoint}")
        ckpt = torch.load(checkpoint, map_location=device)
        if isinstance(ckpt, dict) and "model_state_dict" in ckpt:
            ckpt_hidden = ckpt.get("hidden_sizes")
            if ckpt_hidden and ckpt_hidden != resolved_hidden_sizes:
                print(f"  Using architecture from checkpoint: {ckpt_hidden}")
                resolved_hidden_sizes = ckpt_hidden

    model = NNUE(hidden_sizes=resolved_hidden_sizes).to(device)
    criterion = nn.MSELoss()
    optimizer = optim.Adam(model.parameters(), lr=lr, weight_decay=weight_decay)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)
    scaler = torch.amp.GradScaler('cuda') if torch.cuda.is_available() else torch.amp.GradScaler('cpu')

    if checkpoint:
        ckpt = torch.load(checkpoint, map_location=device)
        if isinstance(ckpt, dict) and "model_state_dict" in ckpt:
            model.load_state_dict(ckpt["model_state_dict"])
            optimizer.load_state_dict(ckpt["optimizer_state_dict"])
            scheduler.load_state_dict(ckpt["scheduler_state_dict"])
            scaler.load_state_dict(ckpt["scaler_state_dict"])
            start_epoch = ckpt["epoch"] + 1
            best_val_loss = ckpt.get("best_val_loss", float('inf'))
            print(f"Resumed from epoch {start_epoch} (best val loss so far: {best_val_loss:.6f})")
        else:
            model.load_state_dict(ckpt)
            print("Loaded weights-only checkpoint (no optimizer state)")

    checkpoint_val_loss = best_val_loss if checkpoint else float('inf')

    subsample_size = max(1, int(subsample_ratio * len(train_dataset)))
    arch_str = " → ".join(str(s) for s in [768] + resolved_hidden_sizes + [1])
    print(f"Architecture: {arch_str}")
    print(f"Training for {epochs} epochs with batch_size={batch_size}, lr={lr}...")
    print(f"Learning rate scheduler: Cosine annealing (T_max={epochs})")
    print(f"Mixed precision training: enabled")
    print(f"Regularization: Dropout (20%) + L2 weight decay ({weight_decay})")
    if subsample_ratio < 1.0:
        print(f"Stochastic sampling: {subsample_ratio:.0%} of train set per epoch ({subsample_size:,} positions)")
    if early_stopping_patience:
        print(f"Early stopping enabled (patience: {early_stopping_patience} epochs)")
    print()

    training_start_time = datetime.now()

    best_val_loss, best_model_state, last_epoch = _run_training_season(
        model, optimizer, scheduler, scaler,
        train_loader, val_loader, train_dataset, val_dataset,
        device, criterion, output_file,
        start_epoch, epochs, early_stopping_patience,
        training_start_time
    )

    if best_model_state is None or best_val_loss >= checkpoint_val_loss:
        print(f"\nNo improvement over checkpoint (best: {best_val_loss:.6f} vs checkpoint: {checkpoint_val_loss:.6f})")
        print("No new model created.")
        return

    _save_versioned_model(
        best_model_state, optimizer, scheduler, scaler, last_epoch,
        best_val_loss, output_file, use_versioning, num_positions,
        stockfish_depth, training_start_time,
        hidden_sizes=resolved_hidden_sizes,
        extra_metadata={"epochs": epochs, "batch_size": batch_size, "learning_rate": lr,
                        "checkpoint": str(checkpoint) if checkpoint else None}
    )

def burst_train(data_file, output_file="nnue_weights.pt", duration_minutes=60,
                epochs_per_season=50, early_stopping_patience=10,
                batch_size=16384, lr=0.001, initial_checkpoint=None,
                stockfish_depth=12, use_versioning=True,
                weight_decay=1e-4, subsample_ratio=1.0, hidden_sizes=None):
    """Train in burst mode: repeatedly restart from the best checkpoint until the time budget expires.

    Each season trains with early stopping. When early stopping fires, the model reloads the
    global best weights and begins a fresh season with a reset optimizer and scheduler.
    This prevents the model from drifting away from its best known state.

    Args:
        data_file: Path to training_data.jsonl
        output_file: Output file base name
        duration_minutes: Total training budget in minutes
        epochs_per_season: Max epochs per restart season (default: 50)
        early_stopping_patience: Patience for early stopping within each season (default: 10)
        batch_size: Training batch size (default: 16384)
        lr: Learning rate reset to this value at the start of each season (default: 0.001)
        initial_checkpoint: Optional weights-only .pt file to start from
        stockfish_depth: Depth used in Stockfish evaluation (for metadata)
        use_versioning: If True, save as nnue_weights_v{N}.pt with metadata
        weight_decay: L2 regularization strength (default: 1e-4)
        subsample_ratio: Fraction of training data to sample per epoch (default: 1.0)
        hidden_sizes: Hidden layer sizes (default: [1536, 1024, 512, 256])
    """
    deadline = datetime.now() + timedelta(minutes=duration_minutes)

    device, dataset, train_dataset, val_dataset, train_loader, val_loader, num_positions = \
        _setup_training(data_file, batch_size, subsample_ratio)

    resolved_hidden_sizes = list(hidden_sizes or DEFAULT_HIDDEN_SIZES)

    if initial_checkpoint:
        print(f"Loading initial weights: {initial_checkpoint}")
        ckpt = torch.load(initial_checkpoint, map_location=device)
        if isinstance(ckpt, dict) and "model_state_dict" in ckpt:
            ckpt_hidden = ckpt.get("hidden_sizes")
            if ckpt_hidden and ckpt_hidden != resolved_hidden_sizes:
                print(f"  Using architecture from checkpoint: {ckpt_hidden}")
                resolved_hidden_sizes = ckpt_hidden

    model = NNUE(hidden_sizes=resolved_hidden_sizes).to(device)
    criterion = nn.MSELoss()
    best_global_val_loss = float('inf')

    if initial_checkpoint:
        ckpt = torch.load(initial_checkpoint, map_location=device)
        if isinstance(ckpt, dict) and "model_state_dict" in ckpt:
            model.load_state_dict(ckpt["model_state_dict"])
            best_global_val_loss = ckpt.get("best_val_loss", float('inf'))
            if best_global_val_loss < float('inf'):
                print(f"Resumed from checkpoint (best val loss: {best_global_val_loss:.6f})")
            else:
                print("Initial weights loaded (no val loss in checkpoint).")
        else:
            model.load_state_dict(ckpt)
            print("Loaded weights-only checkpoint (no val loss info).")

    arch_str = " → ".join(str(s) for s in [768] + resolved_hidden_sizes + [1])
    print(f"Architecture: {arch_str}")
    print(f"Burst training: {duration_minutes}m budget, {epochs_per_season} epochs/season, patience={early_stopping_patience}")
    print(f"Deadline: {deadline.strftime('%H:%M:%S')}")
    print()

    burst_start_time = datetime.now()
    season = 0
    best_global_state = None
    last_optimizer = None
    last_scheduler = None
    last_scaler = None
    last_epoch = 0

    while datetime.now() < deadline:
        season += 1
        remaining_minutes = (deadline - datetime.now()).total_seconds() / 60
        print(f"\n{'=' * 60}")
        print(f"BURST SEASON {season} | {remaining_minutes:.1f} minutes remaining")
        if best_global_val_loss < float('inf'):
            print(f"Global best val loss so far: {best_global_val_loss:.6f}")
        print(f"{'=' * 60}\n")

        optimizer = optim.Adam(model.parameters(), lr=lr, weight_decay=weight_decay)
        scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs_per_season)
        scaler = torch.amp.GradScaler('cuda') if torch.cuda.is_available() else torch.amp.GradScaler('cpu')

        season_start_time = datetime.now()
        val_loss, model_state, last_epoch = _run_training_season(
            model, optimizer, scheduler, scaler,
            train_loader, val_loader, train_dataset, val_dataset,
            device, criterion, output_file,
            0, epochs_per_season, early_stopping_patience,
            season_start_time, deadline=deadline,
            initial_best_val_loss=best_global_val_loss
        )

        last_optimizer = optimizer
        last_scheduler = scheduler
        last_scaler = scaler

        if model_state is not None and val_loss < best_global_val_loss:
            best_global_val_loss = val_loss
            best_global_state = model_state
            print(f"  New global best: {best_global_val_loss:.6f} (season {season})")

        # Reload global best for the next season so we never drift backwards
        if best_global_state is not None:
            model.load_state_dict(best_global_state)

    total_minutes = (datetime.now() - burst_start_time).total_seconds() / 60
    print(f"\n{'=' * 60}")
    print(f"Burst training complete: {season} season(s) in {total_minutes:.1f}m")
    print(f"Best val loss: {best_global_val_loss:.6f}")
    print(f"{'=' * 60}\n")

    if best_global_state is None:
        print("No model improvement found. No file saved.")
        return

    _save_versioned_model(
        best_global_state, last_optimizer, last_scheduler, last_scaler, last_epoch,
        best_global_val_loss, output_file, use_versioning, num_positions,
        stockfish_depth, burst_start_time,
        hidden_sizes=resolved_hidden_sizes,
        extra_metadata={
            "mode": "burst",
            "duration_minutes": duration_minutes,
            "epochs_per_season": epochs_per_season,
            "early_stopping_patience": early_stopping_patience,
            "seasons_completed": season,
            "batch_size": batch_size,
            "learning_rate": lr,
            "initial_checkpoint": str(initial_checkpoint) if initial_checkpoint else None,
        }
    )

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Train NNUE neural network for chess evaluation")
    parser.add_argument("data_file", nargs="?", default="training_data.jsonl",
                        help="Path to training_data.jsonl (default: training_data.jsonl)")
    parser.add_argument("output_file", nargs="?", default="nnue_weights.pt",
                        help="Output file base name (default: nnue_weights.pt)")
    parser.add_argument("--checkpoint", type=str, default=None,
                        help="Path to checkpoint file to resume training from (optional)")
    parser.add_argument("--epochs", type=int, default=100,
                        help="Number of epochs to train (default: 100)")
    parser.add_argument("--batch-size", type=int, default=16384,
                        help="Batch size (default: 16384)")
    parser.add_argument("--lr", type=float, default=0.001,
                        help="Learning rate (default: 0.001)")
    parser.add_argument("--early-stopping", type=int, default=None,
                        help="Stop if val loss doesn't improve for N epochs (optional)")
    parser.add_argument("--stockfish-depth", type=int, default=12,
                        help="Stockfish depth used for evaluations (for metadata, default: 12)")
    parser.add_argument("--no-versioning", action="store_true",
                        help="Disable automatic versioning (save directly to output file)")
    parser.add_argument("--weight-decay", type=float, default=5e-5,
                        help="L2 regularization strength (default: 1e-4, helps prevent overfitting)")
    parser.add_argument("--subsample-ratio", type=float, default=1.0,
                        help="Fraction of training data to sample per epoch (default: 1.0 = all data)")
    parser.add_argument("--hidden-layers", type=str, default=None,
                        help="Comma-separated hidden layer sizes (default: 1536,1024,512,256)")

    # Burst mode
    parser.add_argument("--burst-duration", type=float, default=None,
                        help="Enable burst mode: total training budget in minutes")
    parser.add_argument("--epochs-per-season", type=int, default=50,
                        help="Max epochs per burst season before restarting (default: 50, burst mode only)")

    args = parser.parse_args()

    hidden_sizes = [int(x) for x in args.hidden_layers.split(",")] if args.hidden_layers else None

    if args.burst_duration is not None:
        burst_train(
            data_file=args.data_file,
            output_file=args.output_file,
            duration_minutes=args.burst_duration,
            epochs_per_season=args.epochs_per_season,
            early_stopping_patience=args.early_stopping or 10,
            batch_size=args.batch_size,
            lr=args.lr,
            initial_checkpoint=args.checkpoint,
            stockfish_depth=args.stockfish_depth,
            use_versioning=not args.no_versioning,
            weight_decay=args.weight_decay,
            subsample_ratio=args.subsample_ratio,
            hidden_sizes=hidden_sizes,
        )
    else:
        train_nnue(
            data_file=args.data_file,
            output_file=args.output_file,
            epochs=args.epochs,
            batch_size=args.batch_size,
            lr=args.lr,
            checkpoint=args.checkpoint,
            stockfish_depth=args.stockfish_depth,
            use_versioning=not args.no_versioning,
            early_stopping_patience=args.early_stopping,
            weight_decay=args.weight_decay,
            subsample_ratio=args.subsample_ratio,
            hidden_sizes=hidden_sizes,
        )
