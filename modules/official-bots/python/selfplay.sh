#!/usr/bin/env bash
# Standalone bot self-play -> FENs for labeling. No microservices.
#
#   ./selfplay.sh                  # 500 games with the bundled net
#   ./selfplay.sh 2000             # more games
#   ./selfplay.sh 2000 path.nbai   # play with a specific net
set -euo pipefail

GAMES="${1:-500}"
WEIGHTS="${2:-}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
OUT="$SCRIPT_DIR/data/selfplay.txt"
cd "$REPO_ROOT"

SP_ARGS="--games $GAMES --out $OUT"
if [[ -n "$WEIGHTS" ]]; then
  SP_ARGS="$SP_ARGS --weights $WEIGHTS"
fi

./gradlew -q :modules:official-bots:selfPlay -PspArgs="$SP_ARGS"
echo "Self-play FENs -> $OUT"
