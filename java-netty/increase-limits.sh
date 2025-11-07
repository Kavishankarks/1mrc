#!/bin/bash

# Script to increase system limits for high-concurrency load testing on macOS

echo "Increasing system limits for 1MRC load testing..."
echo "=================================================="

# Increase file descriptor limits
echo "Current soft limit: $(ulimit -n)"
echo "Current hard limit: $(ulimit -Hn)"

# Set to maximum
ulimit -n 65536 2>/dev/null || ulimit -n 12288

echo "New file descriptor limit: $(ulimit -n)"
echo ""

# Show network settings
echo "Network settings:"
sysctl net.inet.ip.portrange.first net.inet.ip.portrange.last 2>/dev/null || echo "Using defaults"
echo ""

echo "Recommended sysctl settings (run with sudo):"
echo "  sudo sysctl -w net.inet.tcp.msl=1000          # Reduce TIME_WAIT"
echo "  sudo sysctl -w kern.maxfiles=65536            # Max open files"
echo "  sudo sysctl -w kern.maxfilesperproc=32768     # Max per process"
echo "  sudo sysctl -w net.inet.ip.portrange.first=10000  # More ephemeral ports"
echo ""

echo "To make permanent, add to /etc/sysctl.conf:"
echo "  net.inet.tcp.msl=1000"
echo "  kern.maxfiles=65536"
echo "  kern.maxfilesperproc=32768"
echo "  net.inet.ip.portrange.first=10000"
echo ""

echo "Run this script before load testing:"
echo "  source ./increase-limits.sh"