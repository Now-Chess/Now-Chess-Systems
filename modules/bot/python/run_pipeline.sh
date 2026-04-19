#!/bin/bash

# NNUE Training Pipeline (bash version)
# Uses the central CLI (nnue.py) for all operations
# Works on Linux, macOS, and Windows (with Git Bash or WSL)

set -e  # Exit on error

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Use python or python3 (check which is available)
PYTHON_CMD="python3"
if ! command -v python3 &> /dev/null; then
    PYTHON_CMD="python"
fi

echo "=== NNUE Training Pipeline ==="
echo ""
echo "Python command: $PYTHON_CMD"
echo "Working directory: $SCRIPT_DIR"
echo ""

# Run the unified training pipeline
$PYTHON_CMD nnue.py train

if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Training pipeline failed"
    exit 1
fi

echo ""
echo "=== Pipeline Complete ==="
echo ""
echo "Next steps:"
echo "1. Navigate to project root: cd ../.."
echo "2. Compile: ./compile"
echo "3. Test: ./test"
