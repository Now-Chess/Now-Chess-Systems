# Concept: NNUE Training Data — Quality, Scale, and Transfer to Colab

Local generation + labeling is **not** a constraint (Ryzen 9800X3D / RTX 5070 / 32 GB).
So the design splits cleanly:

- **Data plane = local box.** Generate, label, shard, publish. Cheap, fast, no limits.
- **Train plane = Colab.** Pull a dataset version, GPU-train, export `.nbai`.

Colab never runs Stockfish and never sees a browser upload. Three problems below:
**(1) good data, (2) growing it over time, (3) getting it there easily** — (3) is the priority.

---

## 1. Generating *good* training sets

### The current weak spot

`generate.py` plays **fully random games** (`random.choice(legal_moves)`). Random play
produces positions that never occur in real games — material chaos, nonsense pawn
structures. An NNUE trained on that learns to evaluate a distribution it will never
face. Fine as filler, wrong as the backbone.

### What a good NNUE dataset needs

1. **Realistic position distribution.** Positions should resemble what the bot actually
   reaches in search — from real games and engine play, not coin-flip moves.
2. **Phase coverage.** Openings, middlegames, endgames all represented. Endgames are
   under-sampled by random play and matter most for precise eval.
3. **Eval balance.** Real game data is dominated by near-equal positions. If 80% of
   labels sit in `[-0.5, +0.5]`, the net learns "everything is roughly equal." Resample
   to flatten the eval histogram (cap per-bucket counts).
4. **Accurate labels.** Deeper Stockfish = better target. Locally you can afford
   depth 16–20. Or skip labeling entirely with the Lichess eval DB (below).
5. **Clean positions.** Dedup by FEN; drop terminal/checkmate/stalemate; the side to
   move should not already be in check unless intended; tag the game phase.

### Recommended source mix (per dataset version)

| Source | Role | How | Weight |
|---|---|---|---|
| **Lichess eval DB** | Backbone | `lichess_importer.py` — millions of FENs **pre-labeled** by deep Stockfish, real human positions, correct sign convention | 50–70% |
| **Engine self-play** | Bot's own distribution | NNUEBot (or vs Stockfish) plays games; sample positions; label with local Stockfish | 20–40% |
| **Tactical puzzles** | Sharp/critical positions | `tactical_positions_extractor.py` (Lichess puzzle DB) | 5–15% |
| **Random play** | Cheap diversity filler | existing `generate.py`, capped low | ≤10% |

The backbone is real, pre-labeled data — so labeling cost is near zero and quality is
high. Self-play is the part that adapts data to *your* bot. Random play stays only as
a thin diversity sprinkle.

### Self-play flywheel (the quality engine over time)

The strongest lever: **net N generates the games that train net N+1.**

```
net_vN  ──play self-play games──►  sample positions  ──label (Stockfish)──►
   ▲                                                                        │
   └──────────────── train on (backbone + new self-play) ◄─────────────────┘
                                  net_v(N+1)
```

Each generation, the bot reaches positions closer to its real playing distribution,
labels them with a stronger-than-bot oracle (Stockfish), and learns the gap. Standard
modern NNUE practice. Keep the Lichess backbone mixed in every round so the net does
not overfit to its own blind spots.

---

## 2. Scaling datasets over time — append-only shards

Do **not** maintain one growing `labeled.jsonl` and re-copy it. Make a dataset an
**immutable set of shards plus a manifest**:

```
datasets/
  shards/
    lichess_000001.jsonl.zst      # ~50–100k positions each, ~5–10 MB compressed
    lichess_000002.jsonl.zst
    selfplay_v7_000001.jsonl.zst
    tactical_000001.jsonl.zst
    ...
  manifest.json
```

`manifest.json`:

```json
{
  "dataset_version": 7,
  "created": "2026-06-24T...",
  "total_positions": 4200000,
  "scale": 300.0,
  "shards": [
    {"file": "lichess_000001.jsonl.zst", "positions": 100000,
     "sha256": "...", "source": "lichess_eval", "stockfish_depth": 0},
    {"file": "selfplay_v7_000001.jsonl.zst", "positions": 80000,
     "sha256": "...", "source": "selfplay", "net": "v7", "stockfish_depth": 18}
  ]
}
```

Properties this buys:

- **Growth = add shards.** Generate a new batch, label it, write one new shard, append
  one manifest entry. Never touch existing shards. O(new data), not O(total).
- **Provenance.** Each shard records source + net + depth. You can later down-weight or
  drop a bad batch by editing the manifest, no relabeling.
- **Dedup across shards** by FEN hash at build time; record dropped counts in metadata.
- **Reproducible mixes.** A "dataset version" is just a manifest selecting shards +
  per-source sampling weights. Cheap to define many mixes over the same shard pool.
- **Resumable, cache-friendly transfer** (next section) — the whole reason for shards.

`dataset.py`'s existing `ds_vN` + `metadata.json` scheme generalizes to this directly:
the dataset dir holds `shards/` + `manifest.json` instead of one `labeled.jsonl`.

---

## 3. Getting data to Colab easily  ← top priority

Shards make this trivial: **incremental sync, never a full re-upload.**

### Recommended: rclone → Google Drive, read from mounted Drive

Colab mounts Drive natively, so the cheapest path is to make Drive the shard store and
sync into it with `rclone` (only uploads new/changed shards):

```bash
# Local, after building shards:
rclone copy datasets/ gdrive:NowChess/datasets --progress
#   ^ uploads only shards Drive doesn't have yet. Adding 80k positions = one small file.
```

Colab side, one cell:

```python
SRC = '/content/drive/MyDrive/NowChess/datasets'   # mounted, no download
import json, shutil, pathlib
manifest = json.load(open(f'{SRC}/manifest.json'))
local = pathlib.Path('/content/datasets'); local.mkdir(exist_ok=True)
for sh in manifest['shards']:                       # copy Drive→local SSD (fast seq read)
    dst = local / sh['file']
    if not dst.exists():                            # cache: only copy missing shards
        shutil.copy(f"{SRC}/shards/{sh['file']}", dst)
```

Why this wins on "easy":
- **No browser upload, ever.** One `rclone copy` from your PC.
- **Incremental both directions.** Add a shard locally → next `rclone copy` ships only
  that shard. Colab copies only shards it doesn't already have on `/content`.
- **Zero new infra.** Drive is already mounted in the notebook.

### Alternative: Gitea release per dataset version (if Drive quota hurts)

You self-host `git.janis-eccarius.de`. Tag `ds_v7`, attach shards + `manifest.json` as
release assets. Colab reads the manifest, then parallel-`wget` only the shards it lacks
(checksum-verified). Versioned, immutable, no Drive quota, token-gated. Slightly more
wiring than rclone→Drive.

Pick rclone→Drive for minimum friction; Gitea releases if you want hard versioning and
to keep Drive small.

### Notebook changes either way

- Clone repo to **ephemeral `/content`** (fast), not Drive. Persist only datasets +
  checkpoints.
- Drop Option A (no Colab generation) and Option B (no browser upload). One "sync
  dataset version" cell instead.
- Train reads shards via a streaming `.jsonl.zst` loader (apply per-source sampling
  weights + eval-bucket balancing here). Keep burst-train + Drive checkpoints + `.nbai`
  export.

---

## Resulting workflow

```
LOCAL (9800X3D / RTX5070)                         COLAB (GPU)
─────────────────────────                         ───────────
import Lichess eval DB ─┐
self-play with net_vN  ─┼─► label ─► dedup ─► write new shard(s) ─► manifest++
tactical / random      ─┘                                  │
                                       rclone copy ────────┘
                                       datasets/ → Drive
                                                              │  (only new shards move)
                                                              ▼
                                    sync version → copy missing shards → train (GPU)
                                                              │
                                                       export .nbai
                                                              ▼
                              place in src/main/resources/, rebuild native image
```

## Build order

1. **Shard format + manifest** in `dataset.py`: write/read `shards/*.jsonl.zst` +
   `manifest.json`; dedup-across-shards on build; provenance per shard.
2. **Streaming `.zst` dataloader** in `train.py`: read shards, apply per-source weights
   and eval-bucket balancing.
3. **Self-play generator** in `src/`: NNUEBot/Stockfish self-play → positions → local
   Stockfish label → new shard. This is the scaling engine.
4. **`dataset_sync.py`**: `push` (rclone→Drive or Gitea upload) / `pull` (cache-aware).
5. **Notebook rewrite**: ephemeral clone, single sync cell, weighted streaming loader.
6. Wire `lichess_importer.py` as the backbone shard source.

## Open decisions

- **Transfer backend** — rclone→Drive (easiest, recommended) vs Gitea releases (hard
  versioning).
- **Self-play opponent** — NNUEBot vs itself (own distribution) vs vs-Stockfish
  (stronger, more decisive games). Likely a mix.
- **Backbone/self-play ratio** — start ~60/30/10 (lichess/selfplay/tactical), tune by
  measured strength.
- **Shard size** — 50k vs 100k positions/shard (transfer granularity vs file count).
