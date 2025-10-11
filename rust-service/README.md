# Rust 1MRC Service

High-performance Rust implementation for the One Million Request Challenge.

## Prerequisites

Install Rust and Cargo:
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source ~/.cargo/env
```

## Running the Service

```bash
cd rust-service
cargo build --release
cargo run --release
```

The service will start on `http://localhost:3000`

## Endpoints

- `POST /event` - Submit event data
  ```json
  {
    "userId": "string",
    "value": number
  }
  ```

- `GET /stats` - Get aggregated statistics
  ```json
  {
    "totalRequests": 1000000,
    "uniqueUsers": 74521,
    "sum": 491829103,
    "avg": 491.8
  }
  ```

## Performance Features

- **Lock-free operations**: Uses atomic counters for high concurrency
- **Concurrent hash set**: DashSet for thread-safe user tracking
- **Memory efficient**: Minimal allocations in hot path
- **Async I/O**: Tokio runtime for maximum throughput
- **Precision handling**: Integer arithmetic for floating point sums

## Load Testing

Use the provided load test client or any HTTP benchmarking tool like `wrk`:

```bash
wrk -t12 -c400 -d30s -s post.lua http://localhost:3000/event
```

Where `post.lua` contains:
```lua
wrk.method = "POST"
wrk.body = '{"userId":"user123","value":42.5}'
wrk.headers["Content-Type"] = "application/json"
```