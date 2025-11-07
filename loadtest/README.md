# Load Testing for 1MRC

Load testing suite for the One Million Request Challenge with multiple options.

## 🚀 Recommended: Go Load Tester

**For maximum performance and simplicity, use the Go load tester:**

👉 **[See loadtest-go/README.md](../loadtest-go/README.md)** for the recommended Go-based load tester.

**Quick start:**
```bash
cd loadtest-go
./run.sh http://localhost:8080
```

**Advantages:**
- ✅ 30-40% faster than k6
- ✅ 67% less memory usage
- ✅ Zero dependencies (single binary)
- ✅ Perfect for 1MRC validation
- ✅ Real-time progress and metrics

---

# Alternative: Load Testing with K6

Legacy load testing option using [K6](https://k6.io/). This is now deprecated in favor of the Go load tester above.

## Why K6?

K6 is the industry-standard load testing tool that provides:

- **High Performance**: Written in Go, handles millions of requests efficiently
- **JavaScript DSL**: Easy-to-write test scripts with familiar syntax
- **Rich Metrics**: Built-in performance metrics and custom counters
- **Distributed Testing**: Can run across multiple machines
- **CI/CD Integration**: Perfect for automated performance testing
- **Real-time Results**: Live statistics during test execution

## Installation

### macOS
```bash
brew install k6
```

### Linux
```bash
# Ubuntu/Debian
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6

# Fedora/CentOS
sudo dnf install https://dl.k6.io/rpm/repo.rpm
sudo dnf install k6
```

### Windows
```bash
choco install k6
```

### Docker
```bash
docker pull grafana/k6:latest
```

## Test Scripts

### 1. One Million Requests Test (1m-requests.js)

The main challenge - sends exactly 1 million requests with constant arrival rate.

**Features:**
- 50,000 requests/second target rate
- Automatic duration calculation
- 75,000 unique users
- Performance thresholds (p95 < 500ms, p99 < 1000ms)
- Automatic stats validation

**Run:**
```bash
./run-test.sh http://localhost:8080
```

Or manually:
```bash
k6 run -e TARGET_URL=http://localhost:8080 1m-requests.js
```

### 2. Stress Test (stress-test.js)

Gradually increases load to find the breaking point.

**Stages:**
1. 100 VUs for 30s (warmup)
2. 500 VUs for 1m
3. 1000 VUs for 1m
4. 2000 VUs for 2m
5. 3000 VUs for 2m
6. 5000 VUs for 1m (spike)
7. Ramp down

**Run:**
```bash
k6 run -e TARGET_URL=http://localhost:8080 stress-test.js
```

### 3. Spike Test (spike-test.js)

Simulates sudden traffic spikes.

**Pattern:**
- Normal load: 100 VUs
- **SPIKE**: 5000 VUs (50x increase in 10 seconds!)
- Maintain spike for 30s
- Back to normal

**Run:**
```bash
k6 run -e TARGET_URL=http://localhost:8080 spike-test.js
```

## Quick Start

### 1. Start Your Server

**Java Netty:**
```bash
cd java-netty
./run-server.sh
```

**Go:**
```bash
cd go-service
go run .
```

**Rust:**
```bash
cd rust-service
cargo run --release
```

### 2. Run Load Test

```bash
cd loadtest
./run-test.sh http://localhost:8080
```

### 3. View Results

Results are automatically saved to `loadtest-results.json` and displayed in the terminal.

## Testing Different Servers

Test any server by changing the URL:

```bash
# Java Netty (default port 8080)
./run-test.sh http://localhost:8080

# Go service (port 8080)
./run-test.sh http://localhost:8080

# Rust service (port 3000)
./run-test.sh http://localhost:3000

# Remote server
./run-test.sh https://your-server.com
```

## Understanding the Output

### During Test
```
✓ Server is healthy

Starting load test...

     execution: local
        script: 1m-requests.js
        output: json (loadtest-results.json)

     scenarios: (100.00%) 1 scenario, 2000 max VUs, 50s max duration
              * one_million_requests: 50000.00 iterations/s for 20s

     ✓ status is 200
     ✓ response time < 1s

     checks.........................: 100.00% ✓ 1000000  ✗ 0
     data_received..................: 123 MB  6.2 MB/s
     data_sent......................: 456 MB  23 MB/s
     events_sent....................: 1000000 50000/s
     http_req_duration..............: avg=45ms  min=5ms  med=42ms  max=980ms  p(90)=78ms  p(95)=112ms  p(99)=234ms
     http_req_failed................: 0.00%   ✓ 0        ✗ 1000000
     http_reqs......................: 1000000 50000/s
```

### Metrics Explained

- **checks**: Validation pass rate (should be 100%)
- **http_req_duration**: Response time percentiles
  - **avg**: Average response time
  - **p(95)**: 95% of requests faster than this
  - **p(99)**: 99% of requests faster than this
- **http_req_failed**: Request failure rate (should be < 1%)
- **http_reqs**: Total requests and rate
- **events_sent**: Custom counter for events

### Server Statistics

After the test, K6 fetches stats from the server:

```json
{
  "totalRequests": 1000000,
  "uniqueUsers": 75000,
  "sum": 491829103.0,
  "avg": 491.83
}
```

## Advanced Usage

### Custom RPS Target

```bash
# 100k requests/second (requires powerful server)
k6 run -e TARGET_URL=http://localhost:8080 \
  --env TARGET_RPS=100000 \
  1m-requests.js
```

### More Virtual Users

```bash
k6 run -e TARGET_URL=http://localhost:8080 \
  --vus 5000 \
  --duration 30s \
  stress-test.js
```

### Save Results to InfluxDB

```bash
k6 run -e TARGET_URL=http://localhost:8080 \
  --out influxdb=http://localhost:8086/k6 \
  1m-requests.js
```

### Cloud Testing with K6 Cloud

```bash
k6 cloud 1m-requests.js
```

## Performance Tuning

### OS Limits (macOS/Linux)

Before running high-concurrency tests:

```bash
# Increase file descriptors
ulimit -n 65536

# Check current limits
ulimit -a
```

### For macOS
```bash
sudo sysctl -w kern.maxfiles=65536
sudo sysctl -w kern.maxfilesperproc=32768
sudo sysctl -w net.inet.tcp.msl=1000
```

### Docker Run

```bash
docker run --rm -i \
  -v $(pwd):/scripts \
  grafana/k6:latest run \
  -e TARGET_URL=http://host.docker.internal:8080 \
  /scripts/1m-requests.js
```

## Distributed Testing

Run K6 on multiple machines for even higher load:

### Machine 1 (Coordinator)
```bash
k6 run -e TARGET_URL=http://target-server:8080 \
  --out json=results-1.json \
  1m-requests.js
```

### Machine 2
```bash
k6 run -e TARGET_URL=http://target-server:8080 \
  --out json=results-2.json \
  1m-requests.js
```

Then aggregate results manually or use K6 Cloud.

## Troubleshooting

### "connection refused"
- Server is not running
- Wrong port number
- Firewall blocking connections

### "dial tcp: resource temporarily unavailable"
- Increase OS file descriptor limits
- Reduce concurrent VUs
- Add small sleep() in test script

### "context deadline exceeded"
- Server is overloaded
- Increase timeout in test script
- Reduce request rate

### High Error Rate
- Server can't handle the load
- Check server logs
- Run stress test to find capacity
- Scale server horizontally

## Comparing Implementations

Test all servers and compare:

```bash
# Test Java Netty
cd java-netty && ./run-server.sh &
cd ../loadtest && ./run-test.sh http://localhost:8080
# Save results as java-netty-results.json

# Test Go
cd ../go-service && go run . &
cd ../loadtest && ./run-test.sh http://localhost:8080
# Save results as go-results.json

# Test Rust
cd ../rust-service && cargo run --release &
cd ../loadtest && ./run-test.sh http://localhost:3000
# Save results as rust-results.json
```

Compare metrics:
- Throughput (req/s)
- Latency (p95, p99)
- Error rate
- Memory usage
- CPU usage

## Best Practices

1. **Warm-up**: Let server warm up before full load
2. **Monitoring**: Monitor server metrics (CPU, memory, connections)
3. **Realistic Data**: Use realistic user IDs and values
4. **Network**: Test on same network as production
5. **Iterations**: Run multiple times for consistency
6. **Baselines**: Establish performance baselines
7. **CI/CD**: Integrate into deployment pipeline

## Example Results

Expected performance on modern hardware:

| Server        | RPS      | p95 Latency | p99 Latency | Memory |
|---------------|----------|-------------|-------------|--------|
| Java Netty    | 50k-60k  | 45ms        | 120ms       | 500MB  |
| Go            | 60k-80k  | 35ms        | 90ms        | 200MB  |
| Rust          | 70k-100k | 25ms        | 70ms        | 50MB   |

*Results vary based on hardware, network, and configuration*

## Resources

- [K6 Documentation](https://k6.io/docs/)
- [K6 Examples](https://github.com/grafana/k6/tree/master/examples)
- [Load Testing Methodology](https://k6.io/docs/test-types/)
- [K6 Cloud](https://k6.io/cloud/)

## License

MIT