#!/bin/bash

# Go Load Test Runner for 1MRC
# High-performance load testing with zero overhead

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
TARGET_URL="${1:-http://localhost:8080}"
TOTAL_REQUESTS="${2:-1000000}"
WORKERS="${3:-500}"
RPS="${4:-0}"

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║        One Million Request Challenge - Go Load Tester        ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if Go is installed
if ! command -v go &> /dev/null; then
    echo -e "${RED}❌ Go is not installed${NC}"
    echo ""
    echo "Install Go from: https://golang.org/dl/"
    exit 1
fi

echo -e "${GREEN}✓ Go version: $(go version | awk '{print $3}')${NC}"
echo ""

# Build if needed
if [ ! -f "./loadtest" ] || [ "main.go" -nt "./loadtest" ]; then
    echo -e "${YELLOW}Building load tester...${NC}"
    go build -o loadtest main.go
    echo -e "${GREEN}✓ Build complete${NC}"
    echo ""
fi

# Run load test
if [ "$RPS" != "0" ]; then
    ./loadtest -url="$TARGET_URL" -n="$TOTAL_REQUESTS" -workers="$WORKERS" -rps="$RPS"
else
    ./loadtest -url="$TARGET_URL" -n="$TOTAL_REQUESTS" -workers="$WORKERS"
fi

exit $?