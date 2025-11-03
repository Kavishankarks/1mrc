#!/bin/bash

# ActiveJ Server Runner for 1MRC
# Ultra-high performance async server with zero allocations

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PORT="${1:-8080}"

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     One Million Request Challenge - ActiveJ Server           ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java is not installed${NC}"
    echo ""
    echo "Install Java 21+:"
    echo "  macOS:   brew install openjdk@21"
    echo "  Linux:   sudo apt install openjdk-21-jdk"
    echo ""
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}❌ Java 17+ is required (you have Java $JAVA_VERSION)${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Java version: $(java -version 2>&1 | head -n 1)${NC}"

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven is not installed${NC}"
    echo ""
    echo "Install Maven:"
    echo "  macOS:   brew install maven"
    echo "  Linux:   sudo apt install maven"
    echo ""
    exit 1
fi

echo -e "${GREEN}✓ Maven version: $(mvn -version | head -n 1)${NC}"
echo ""

# Clean and build
echo -e "${YELLOW}Building ActiveJ server...${NC}"
mvn clean package -DskipTests -q

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Build successful${NC}"
    echo ""
else
    echo -e "${RED}❌ Build failed${NC}"
    exit 1
fi

# Run the server
echo -e "${YELLOW}Starting ActiveJ server on port ${PORT}...${NC}"
echo ""

java -server \
    -Xms512m -Xmx512m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=20 \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -Dhttp.listenPort=${PORT} \
    -jar target/onemrc-activej-1.0.0.jar

exit $?