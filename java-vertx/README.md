# One Million Request Challenge - Vert.x Implementation

High-performance implementation of the 1MRC using Eclipse Vert.x and Java Virtual Threads (Project Loom).

## Overview

This implementation uses:
- **Vert.x 4.5.1**: Reactive toolkit for high-performance, non-blocking I/O
- **Java Virtual Threads (Project Loom)**: Lightweight threads for handling business logic
- **Lock-free concurrent data structures**: AtomicLong, DoubleAdder, ConcurrentHashMap
- **Optimized HTTP server configuration**: TCP_NODELAY, SO_REUSEPORT, increased buffers

## Key Features

- Event loop for asynchronous I/O operations
- Virtual threads for request processing to avoid blocking the event loop
- Lock-free aggregation for maximum throughput
- Thread-safe concurrent operations
- Optimized network settings for low latency

## Requirements

- Java 21 or higher (for Virtual Threads support)
- Maven 3.6+

## Building

```bash
mvn clean package
```

This creates an uber JAR with all dependencies in `target/onemrc-vertx-1.0.0.jar`.

## Running

### Using Maven:
```bash
mvn exec:java
```

### Using the JAR directly:
```bash
java -jar target/onemrc-vertx-1.0.0.jar
```

### Custom port:
```bash
java -jar target/onemrc-vertx-1.0.0.jar 8080
```

## API Endpoints

### POST /event
Submit an event for processing.

**Request:**
```json
{
  "userId": "user123",
  "value": 42.5
}
```

**Response:**
```json
{
  "status": "ok"
}
```

### GET /stats
Get aggregated statistics.

**Response:**
```json
{
  "totalRequests": 1000000,
  "uniqueUsers": 74521,
  "sum": 491829103,
  "avg": 491.8
}
```

### GET /health
Health check endpoint.

**Response:**
```json
{
  "status": "healthy"
}
```

## Performance Optimizations

1. **Vert.x Event Loop**: Non-blocking I/O for network operations
2. **Virtual Threads**: Each request is processed on a virtual thread, avoiding thread pool exhaustion
3. **Lock-free Data Structures**:
   - `AtomicLong` for request counting
   - `DoubleAdder` for sum aggregation
   - `ConcurrentHashMap` for unique user tracking
4. **Network Tuning**:
   - TCP_NODELAY: Disabled Nagle's algorithm
   - SO_REUSEPORT: Better load distribution across cores
   - Increased buffer sizes (64KB)
   - High accept backlog (8192)

## Architecture

```
┌─────────────────┐
│  HTTP Request   │
└────────┬────────┘
         │
         v
┌─────────────────┐
│  Vert.x Router  │  (Event Loop - Non-blocking)
└────────┬────────┘
         │
         v
┌─────────────────┐
│ Virtual Thread  │  (Request Handler)
└────────┬────────┘
         │
         v
┌─────────────────┐
│ EventStorage    │  (Lock-free Operations)
│    Service      │
└─────────────────┘
```

## Testing

You can test the server using curl:

```bash
# Submit an event
curl -X POST http://localhost:8080/event \
  -H "Content-Type: application/json" \
  -d '{"userId": "user123", "value": 42.5}'

# Get statistics
curl http://localhost:8080/stats

# Health check
curl http://localhost:8080/health
```

## Load Testing

For the full 1 million request challenge, use a load testing tool like `wrk`, `hey`, or the included load test client from the parent project.

Example with `wrk`:
```bash
wrk -t12 -c400 -d30s --latency \
  -s post.lua \
  http://localhost:8080/event
```

## Logging

Logs are written to:
- Console (INFO level)
- `logs/vertx-server.log` (DEBUG level with rotation)

## License

Part of the 1MRC project.