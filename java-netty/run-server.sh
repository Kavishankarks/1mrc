#!/bin/bash

# Run Netty Server for One Million Request Challenge
# Optimized JVM settings for high throughput

echo "Building Netty Server..."
mvn clean package -DskipTests -q

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo "Starting Netty Server with optimized JVM settings..."
echo "=============================================="

java -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+UseStringDeduplication \
     -XX:+AlwaysPreTouch \
     -Xms512m \
     -Xmx2g \
     -jar target/onemrc-netty-1.0.0.jar ${1:-8080}