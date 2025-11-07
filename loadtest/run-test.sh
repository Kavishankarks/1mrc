#!/bin/bash

# K6 Load Test Runner for 1MRC
# Run 1 million requests against any server

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
TARGET_URL="${1:-http://localhost:8080}"
SCRIPT="${2:-1m-requests.js}"

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║        One Million Request Challenge - K6 Load Test          ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if K6 is installed
if ! command -v k6 &> /dev/null; then
    echo -e "${RED}❌ K6 is not installed${NC}"
    echo ""
    echo "Install K6:"
    echo "  macOS:   brew install k6"
    echo "  Linux:   sudo snap install k6"
    echo "  Windows: choco install k6"
    echo ""
    echo "Or download from: https://k6.io/docs/getting-started/installation/"
    exit 1
fi

echo -e "${GREEN}✓ K6 version: $(k6 version)${NC}"
echo ""

# Check if server is running
echo -e "${YELLOW}Checking server at ${TARGET_URL}...${NC}"

# Try /health first, then /stats as fallback
if curl -sf "${TARGET_URL}/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Server is responding (/health)${NC}"
elif curl -sf "${TARGET_URL}/stats" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Server is responding (/stats)${NC}"
    echo -e "${YELLOW}⚠  Note: /health endpoint not available, using /stats${NC}"
else
    echo -e "${RED}❌ Server is not responding at ${TARGET_URL}${NC}"
    echo ""
    echo "Make sure your server is running:"
    echo "  - Java Netty:  cd java-netty && ./run-server.sh"
    echo "  - Java Spring: cd java-spring && mvn spring-boot:run"
    echo "  - Go:          cd go-service && go run ."
    echo "  - Rust:        cd rust-service && cargo run --release"
    echo ""
    exit 1
fi

echo ""
echo -e "${BLUE}Starting load test...${NC}"
echo ""

# Run K6 test
k6 run \
    -e TARGET_URL="${TARGET_URL}" \
    --out json=loadtest-results.json \
    "${SCRIPT}"

EXIT_CODE=$?

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                   Load Test Completed!                        ║${NC}"
    echo -e "${GREEN}╚═══════════════════════════════════════════════════════════════╝${NC}"
else
    echo -e "${RED}╔═══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║                    Load Test Failed                           ║${NC}"
    echo -e "${RED}╚═══════════════════════════════════════════════════════════════╝${NC}"
fi

echo ""
echo "Results saved to: loadtest-results.json"
echo ""

exit $EXIT_CODE