#!/usr/bin/env python3
"""Export NNUE weights to .nbai format for runtime loading."""

import json
import struct
import sys
from datetime import datetime
from pathlib import Path

import torch

MAGIC = 0x4942_414E   # bytes 'N','B','A','I' as little-endian int32
VERSION = 1


def _read_sidecar(weights_file: str) -> dict:
    sidecar = weights_file.replace(".pt", "_metadata.json")
    if Path(sidecar).exists():
        with open(sidecar) as f:
            return json.load(f)
    return {}


def _infer_layers(state_dict: dict) -> list[dict]:
    """Derive layer descriptors from state_dict weight shapes.

    Assumes layers named l1, l2, ..., lN.
    All hidden layers get activation 'relu'; the last gets 'linear'.
    """
    names = sorted(
        {k.split(".")[0] for k in state_dict if k.endswith(".weight")},
        key=lambda n: int(n[1:]),
    )
    layers = []
    for i, name in enumerate(names):
        out_size, in_size = state_dict[f"{name}.weight"].shape
        activation = "linear" if i == len(names) - 1 else "relu"
        layers.append({"activation": activation, "inputSize": int(in_size), "outputSize": int(out_size)})
    return layers


def _write_floats(f, tensor):
    data = tensor.float().flatten().cpu().numpy()
    f.write(struct.pack("<I", len(data)))
    f.write(struct.pack(f"<{len(data)}f", *data))


def export_to_nbai(
    weights_file: str,
    output_file: str,
    trained_by: str = "unknown",
    train_loss: float = 0.0,
):
    if not Path(weights_file).exists():
        print(f"Error: weights file not found at {weights_file}")
        sys.exit(1)

    loaded = torch.load(weights_file, map_location="cpu")
    state_dict = (
        loaded["model_state_dict"]
        if isinstance(loaded, dict) and "model_state_dict" in loaded
        else loaded
    )

    sidecar = _read_sidecar(weights_file)
    val_loss = float(loaded.get("best_val_loss", sidecar.get("final_val_loss", 0.0))) if isinstance(loaded, dict) else 0.0
    trained_at = sidecar.get("date", datetime.now().isoformat())
    training_data_count = int(sidecar.get("num_positions", 0))

    metadata = {
        "trainedBy": trained_by,
        "trainedAt": trained_at,
        "trainingDataCount": training_data_count,
        "valLoss": val_loss,
        "trainLoss": train_loss,
    }

    layers = _infer_layers(state_dict)
    layer_names = sorted(
        {k.split(".")[0] for k in state_dict if k.endswith(".weight")},
        key=lambda n: int(n[1:]),
    )

    print(f"Architecture ({len(layers)} layers):")
    for i, l in enumerate(layers):
        print(f"  l{i + 1}: {l['inputSize']} -> {l['outputSize']}  [{l['activation']}]")

    Path(output_file).parent.mkdir(parents=True, exist_ok=True)

    with open(output_file, "wb") as f:
        # Header
        f.write(struct.pack("<I", MAGIC))
        f.write(struct.pack("<H", VERSION))

        # Metadata (length-prefixed UTF-8 JSON)
        meta_bytes = json.dumps(metadata, indent=2).encode("utf-8")
        f.write(struct.pack("<I", len(meta_bytes)))
        f.write(meta_bytes)

        # Layer descriptors
        f.write(struct.pack("<H", len(layers)))
        for layer in layers:
            name_bytes = layer["activation"].encode("ascii")
            f.write(struct.pack("<B", len(name_bytes)))
            f.write(name_bytes)
            f.write(struct.pack("<I", layer["inputSize"]))
            f.write(struct.pack("<I", layer["outputSize"]))

        # Weights: weight tensor then bias tensor per layer
        for name in layer_names:
            w = state_dict[f"{name}.weight"]
            b = state_dict[f"{name}.bias"]
            _write_floats(f, w)
            _write_floats(f, b)
            print(f"  Wrote {name}: weight {tuple(w.shape)}, bias {tuple(b.shape)}")

    size_mb = Path(output_file).stat().st_size / (1024 ** 2)
    print(f"\nExported to {output_file} ({size_mb:.2f} MB)")
    print(f"Metadata: {json.dumps(metadata, indent=2)}")


if __name__ == "__main__":
    weights_file = "nnue_weights.pt"
    output_file = "../src/main/resources/nnue_weights.nbai"
    trained_by = "unknown"
    train_loss = 0.0

    if len(sys.argv) > 1:
        weights_file = sys.argv[1]
    if len(sys.argv) > 2:
        output_file = sys.argv[2]
    if len(sys.argv) > 3:
        trained_by = sys.argv[3]
    if len(sys.argv) > 4:
        train_loss = float(sys.argv[4])

    export_to_nbai(weights_file, output_file, trained_by, train_loss)
