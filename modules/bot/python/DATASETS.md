# Training Dataset Management

The NNUE training pipeline now features versioned dataset management, similar to model versioning. This prevents data loss and allows you to maintain multiple training configurations.

## Directory Structure

```
datasets/
  ds_v1/
    labeled.jsonl       # Training data: {"fen": "...", "eval": 0.5, "eval_raw": 150}
    metadata.json       # Version info and composition
  ds_v2/
    labeled.jsonl
    metadata.json
```

## Metadata Schema

Each dataset has a `metadata.json` file tracking its composition:

```json
{
  "version": 1,
  "created": "2026-04-13T15:30:45.123456",
  "total_positions": 1000000,
  "stockfish_depth": 12,
  "sources": [
    {
      "type": "generated",
      "count": 500000,
      "params": {
        "num_positions": 500000,
        "min_move": 1,
        "max_move": 50
      }
    },
    {
      "type": "tactical",
      "count": 300000,
      "max_puzzles": 300000
    },
    {
      "type": "file_import",
      "count": 200000,
      "path": "/path/to/original_file.txt"
    }
  ]
}
```

## TUI Workflow

### Main Menu
```
1 - Manage Training Data
2 - Train Model
3 - Export Model
4 - Exit
```

### Training Data Management Submenu
```
1 - Create new dataset
2 - Extend existing dataset
3 - View all datasets
4 - Delete dataset
5 - Back
```

## Creating a Dataset

Use the "Create new dataset" option to add data from one or more sources:

1. **Generate random positions** — Play random games and sample positions
   - Number of positions
   - Move range (min/max move number to sample from)
   - Number of worker threads

2. **Import from file** — Load positions from a FEN file
   - File must contain one FEN string per line
   - Duplicates are automatically removed

3. **Extract tactical puzzles** — Download and extract Lichess puzzle database
   - Maximum number of puzzles to include
   - Automatically filters for tactical themes (forks, pins, mates, etc.)

You can combine multiple sources in a single dataset creation session. All positions are:
- Deduplicated (only unique FENs are kept)
- Labeled with Stockfish evaluations
- Saved to `datasets/ds_vN/labeled.jsonl`

## Extending a Dataset

Use "Extend existing dataset" to add more positions to an existing dataset:

1. Select the dataset version to extend
2. Choose data sources (same options as creation)
3. Confirm labeling parameters
4. New positions are:
   - Labeled with Stockfish
   - Deduplicated against the target dataset (preventing duplicates)
   - Merged into the existing `labeled.jsonl`
   - Metadata is updated with the new source entry

## Training with a Dataset

When you start training (Standard or Burst mode), you'll be prompted to select a dataset version. The TUI will display all available datasets with:
- Version number
- Total number of positions
- Source types (generated, tactical, imported)
- Stockfish depth used
- Creation date

## Legacy Data Migration

If you have existing labeled data in `data/training_data.jsonl` from before this update:

1. Open the "Manage Training Data" menu
2. Choose "Create new dataset"
3. Select "Import from file"
4. Point to `data/training_data.jsonl`
5. Complete the dataset creation

Alternatively, you can manually copy the file to `datasets/ds_v1/labeled.jsonl` and create a `metadata.json` file.

## Viewing Dataset Details

Use "View all datasets" to see a table of all datasets with:
- Version number
- Position count
- Source composition
- Stockfish depth
- Creation date

## Deleting a Dataset

Use "Delete dataset" to remove a dataset and free up disk space. **This action cannot be undone.**

⚠️ The system does not prevent deleting datasets used by model checkpoints. Plan accordingly.

## Technical Details

### Deduplication Strategy

When extending a dataset, positions are deduplicated **within that dataset only**. This allows different datasets to contain overlapping positions if desired.

When creating a new dataset from multiple sources, all sources are combined and deduplicated before labeling.

### Labeled Position Format

Each line in `labeled.jsonl` is a JSON object:
```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "eval": 0.0,
  "eval_raw": 0
}
```

- `fen`: The position in Forsyth-Edwards Notation
- `eval`: Normalized evaluation ([-1, 1] range using tanh)
- `eval_raw`: Raw Stockfish evaluation in centipawns

### Storage Location

Datasets are stored in the `datasets/` directory relative to the script location. The old `data/` directory is preserved for backward compatibility but is not actively used by the new system.

## Performance Tips

- **Smaller datasets train faster** — Start with 100k-500k positions
- **Deduplication matters** — Use the extend functionality to build up your dataset without redundant data
- **Stockfish depth** — Depth 12-14 balances accuracy and labeling speed
- **Workers** — Use 4-8 workers for labeling if your machine supports it; more workers = faster but uses more CPU/memory
