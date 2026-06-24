#!/usr/bin/env bash
# Build the ENTIRE NNUE training dataset + push to Drive. One command.
#
#   ./dataset.sh                 # build everything + rclone push
#   ./dataset.sh --no-push       # build only
#   ./dataset.sh --no-lichess    # skip the large Lichess backbone
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

PY="python3"
if [[ -x "$SCRIPT_DIR/.venv/bin/python" ]]; then
  PY="$SCRIPT_DIR/.venv/bin/python"
fi

exec "$PY" build_dataset.py "$@"
