# NNUE Python Pipeline

Central CLI for training and exporting chess evaluation neural networks (NNUE).

## Directory Structure

```
python/
├── nnue.py              # Main CLI entry point
├── src/                 # Python modules
│   ├── generate.py      # Generate random chess positions
│   ├── label.py         # Label positions with Stockfish
│   ├── train.py         # Train NNUE model
│   └── export.py        # Export weights to Scala
├── data/                # Training data (gitignored)
│   ├── positions.txt
│   └── training_data.jsonl
└── weights/             # Model weights (gitignored)
    ├── nnue_weights_v1.pt
    ├── nnue_weights_v1_metadata.json
    └── ...
```

## Quick Start

```bash
# Train a new model (500k positions, auto-detect checkpoint)
python nnue.py train

# Train from specific checkpoint
python nnue.py train --from-checkpoint 2

# Train with custom games count
python nnue.py train --games 200000

# Train with custom positions file
python nnue.py train --positions-file my_positions.txt

# Export specific version to Scala
python nnue.py export 2

# List all checkpoints
python nnue.py list
```

## CLI Commands

### `train` - Train NNUE model

```bash
python nnue.py train [OPTIONS]
```

**Options:**
- `--from-checkpoint N` - Resume from checkpoint version N (default: uses latest)
- `--games N` - Number of games to generate (default: 500000)
- `--positions-file FILE` - Use existing positions file instead of generating
- `--stockfish PATH` - Path to Stockfish binary (default: `$STOCKFISH_PATH` or `/usr/games/stockfish`)

**Examples:**
```bash
# Train with latest checkpoint
python nnue.py train

# Train from v2 with 100k games
python nnue.py train --from-checkpoint 2 --games 100000

# Train with custom positions
python nnue.py train --positions-file my_games.txt --stockfish /opt/stockfish/sf15
```

### `export` - Export weights to Scala

```bash
python nnue.py export WEIGHTS [output_path]
```

**Arguments:**
- `WEIGHTS` - Version number (e.g., `2`) or full filename (e.g., `nnue_weights_v2.pt`)

**Examples:**
```bash
# Export version 2
python nnue.py export 2

# Export with full filename
python nnue.py export nnue_weights_v3.pt
```

Output goes to `../src/main/scala/de/nowchess/bot/bots/nnue/NNUEWeights_vN.scala`

### `list` - List available checkpoints

```bash
python nnue.py list
```

Shows all available model versions with file sizes.

## Data Flow

1. **Generate** → `data/positions.txt`
   - Random chess positions from 8-20 move openings
   - Filters out checks, game-over states, and captures

2. **Label** → `data/training_data.jsonl`
   - Evaluates each position with Stockfish at depth 12
   - Stores FEN + evaluation in JSONL format

3. **Train** → `weights/nnue_weights_vN.pt`
   - Trains neural network on labeled positions
   - Auto-versioning (v1, v2, v3, etc.)
   - Saves metadata alongside weights

4. **Export** → `NNUEWeights_vN.scala`
   - Converts weights to Scala object
   - Ready for integration into bot

## Versioning

- Models are automatically versioned (v1, v2, v3, etc.)
- Each version gets a `_metadata.json` file with training info
- Training from checkpoint uses latest version unless specified with `--from-checkpoint`

## Files

- `data/` and `weights/` are gitignored (local artifacts)
- Documentation in `docs/` explains training, debugging, and incremental improvements
- Source modules in `src/` are independent and can be imported for custom workflows
