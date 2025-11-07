#!/bin/bash

# Run Load Test Client for One Million Request Challenge

echo "Running Load Test Client..."
echo "=============================================="
echo "Make sure the server is running on http://localhost:8080"
echo ""

mvn exec:java -Dexec.mainClass="com.recnos.onemrc.LoadTestClient" -q