# Go Service - One Million Request Challenge (1MRC)

High-performance Go HTTP server implementation for handling 1 million concurrent requests.

## Features

- **Thread-safe in-memory storage** using atomic operations
- **Lock-free float64 arithmetic** with compare-and-swap
- **Concurrent request handling** with Go's built-in HTTP server
- **Memory-efficient user tracking** with sync.Map

## Running the Service

```bash
# Start the server
go run main.go

# In another terminal, run the test client
go run test_client.go
```

## API Endpoints

- `POST /event` - Submit events: `{"userId": "string", "value": number}`
- `GET /stats` - Get aggregated statistics

## Performance

- Handles ~85,000+ requests per second
- Zero data races with atomic operations
- Minimal memory allocations
- Scales with available CPU cores

## Architecture

- `EventStore` - Thread-safe storage with atomic counters
- `sync.Map` - Lock-free concurrent user tracking
- Atomic float64 operations using `math.Float64bits`
- HTTP server with configurable timeouts