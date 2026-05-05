#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
TEST_TYPE="${1:-ramp-up}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
OUTPUT_FILE="${2:-results-$(date +%s).csv}"

echo -e "${BLUE}NowChess Load Test Runner${NC}"
echo "Test Type: $TEST_TYPE"
echo "Base URL: $BASE_URL"
echo ""

# Check if k6 is installed
if ! command -v k6 &> /dev/null; then
    echo -e "${RED}Error: k6 not found. Install from https://k6.io/docs/getting-started/installation/${NC}"
    exit 1
fi

case "$TEST_TYPE" in
    ramp-up)
        echo -e "${GREEN}Running Ramp-Up Test (10->100 VUs over 13 minutes)${NC}"
        k6 run --out=csv=$OUTPUT_FILE ramp-up.js
        ;;
    stress)
        echo -e "${GREEN}Running Stress Test (up to 500 VUs)${NC}"
        k6 run --out=csv=$OUTPUT_FILE stress-test.js
        ;;
    spike)
        echo -e "${GREEN}Running Spike Test (sudden 50->500 spike)${NC}"
        k6 run --out=csv=$OUTPUT_FILE spike-test.js
        ;;
    constant)
        echo -e "${GREEN}Running Constant Load Test (50 VUs for 10m)${NC}"
        k6 run --out=csv=$OUTPUT_FILE constant-load.js
        ;;
    all)
        echo -e "${GREEN}Running All Tests${NC}"
        for test in ramp-up stress spike constant; do
            echo ""
            echo -e "${BLUE}Starting $test test...${NC}"
            $0 $test
            sleep 5
        done
        exit 0
        ;;
    *)
        echo -e "${RED}Usage: $0 {ramp-up|stress|spike|constant|all} [output-file]${NC}"
        echo ""
        echo "Examples:"
        echo "  $0 ramp-up"
        echo "  $0 stress results.csv"
        echo "  $0 all"
        exit 1
        ;;
esac

echo -e "${GREEN}Test complete. Results saved to $OUTPUT_FILE${NC}"
