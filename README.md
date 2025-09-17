# One Million Request Challenge (1MRC)

A comprehensive framework for evaluating the performance and robustness of web applications under heavy load by processing **1,000,000 concurrent requests**.

## 🎯 Challenge Overview

The 1MRC tests your ability to build high-performance, thread-safe web services that can handle massive scale. This repository provides implementations in both **Go** and **Java Spring Boot** to demonstrate different approaches to solving the same performance challenge.

### Problem Definition

**Implement an HTTP server with the following endpoints:**

1. **POST /event** - Accept event data
   ```json
{
  "userId": "string", 
  "value": "number"
}
```

2. **GET /stats** - Return aggregated statistics
   ```json
   {
     "totalRequests": 1000000,
     "uniqueUsers": 75000,
     "sum": 499500000.0,
     "avg": 499.5
   }
   ```

**Requirements:**
- ✅ Process **1,000,000 requests** accurately
- ✅ Handle **hundreds/thousands concurrent requests per second**  
- ✅ Maintain **thread safety** (no lost or double-counted requests)
- ✅ Optimize for **maximum throughput**
- ✅ Ensure **data integrity** under high concurrency

## 🏗️ Implementations

This repository contains two complete implementations:

### 🚀 Go Implementation (`go-service/`)

**Performance:** ~85,000 requests/second

**Features:**
- Lock-free atomic operations
- Minimal memory footprint (~50MB)
- Instant startup time
- sync.Map for concurrent user tracking
- Built-in HTTP server optimization

```bash
cd go-service
go run main.go          # Start server
go run test_client.go   # Run 1M request test
```

### ☕ Java Spring Boot Implementation (`java-spring/`)

**Performance:** ~10,000-15,000 requests/second

**Features:**
- Enterprise-grade Spring Boot framework
- Thread-safe AtomicLong and DoubleAdder
- Optimized Tomcat configuration (2000 max threads)
- Built-in monitoring with Actuator
- Comprehensive validation and error handling

```bash
cd java-spring
mvn spring-boot:run     # Start server
mvn exec:java           # Run 1M request test
```

## 📊 Performance Comparison

| Metric | Go | Java Spring Boot |
|--------|----|-----------------| 
| **Throughput** | ~85,000 req/s | ~10,000-15,000 req/s |
| **Memory Usage** | ~50MB | ~200MB+ |
| **Startup Time** | Instant | ~1-2 seconds |
| **CPU Efficiency** | High | Medium |
| **Ecosystem** | Minimal, fast | Enterprise features |
| **Monitoring** | Custom | Built-in Actuator |

## 🎯 Evaluation Criteria

### ✅ Correctness
- **Accurate aggregation**: All 1M requests counted exactly once
- **Thread safety**: No race conditions or data corruption
- **Proper validation**: Handle malformed requests gracefully

### ⚡ Performance  
- **Throughput**: Requests per second under load
- **Latency**: Response time consistency
- **Resource usage**: Memory and CPU efficiency
- **Scalability**: Performance degradation curve

### 🛡️ Robustness
- **Error handling**: Graceful failure modes
- **Memory management**: No leaks under sustained load  
- **Connection handling**: Proper cleanup and limits
- **Monitoring**: Health checks and metrics

## 🧪 Testing & Benchmarking

Both implementations include comprehensive test clients that:

- Send 1,000,000 requests with configurable concurrency
- Track real-time progress (every 100k requests)
- Measure throughput (requests/second)
- Validate final statistics for accuracy
- Generate timestamped logs for analysis

### Sample Output:
```
Starting 1MRC test with 1000000 requests and 1000 concurrent workers
Completed: 100000/1000000 (85346.9 req/s)
Completed: 200000/1000000 (87240.1 req/s)
...
Completed: 1000000/1000000 (85901.9 req/s)

=== Test Results ===
Total time: 11.716943s
Requests per second: 85901.71
Errors: 0

=== Server Stats ===
Total Requests: 1000000
Unique Users: 75000
Sum: 499500000.00
Average: 499.50

✅ SUCCESS: All requests processed correctly!
```

## 🔧 Architecture Highlights

### Go Implementation Design
```go
type EventStore struct {
    totalRequests int64                    // Atomic counter
    sum           uint64                   // Atomic float64 operations  
    users         sync.Map                 // Lock-free concurrent map
    userCount     int64                    // Atomic user counter
}
```

**Key Optimizations:**
- Compare-and-swap for float64 arithmetic
- Zero-copy user existence tracking
- Minimal HTTP server overhead
- Efficient memory allocation patterns

### Java Spring Boot Design
```java
@Service
public class EventStorageService {
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final DoubleAdder sum = new DoubleAdder();           // High-perf summation
    private final ConcurrentHashMap<String, Boolean> users;     // Thread-safe map
}
```

**Key Optimizations:**
- DoubleAdder for high-contention scenarios
- Pre-sized ConcurrentHashMap with optimal concurrency
- Tomcat tuning (2000 threads, 20K connections)
- JVM optimization (G1GC, 4GB heap)

## 🚀 Quick Start

### Prerequisites
- **Go**: 1.25+ (for Go implementation)
- **Java**: 17+ and Maven 3.6+ (for Java implementation)

### Running the Challenge

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd 1mrc
   ```

2. **Choose your implementation**

   **Go (Recommended for maximum performance):**
   ```bash
   cd go-service
   go run main.go &           # Start server in background
   go run test_client.go      # Run the challenge
   ```

   **Java Spring Boot (Enterprise features):**
   ```bash
   cd java-spring
   export MAVEN_OPTS="-Xmx4g -Xms4g -XX:+UseG1GC"
   mvn spring-boot:run &      # Start server in background  
   mvn exec:java              # Run the challenge
   ```

3. **Monitor results** in real-time and check logs in `logs/` directory

## 📈 Optimization Techniques

### Go Optimizations
- **Atomic operations**: Lock-free concurrent programming
- **Memory pooling**: Reuse objects to reduce GC pressure
- **HTTP keep-alive**: Connection reuse for client efficiency
- **Compiler optimizations**: Built-in Go runtime efficiency

### Java Optimizations  
- **JVM tuning**: G1GC with optimized pause times
- **Thread pool sizing**: Balance between throughput and resource usage
- **Connection pooling**: HTTP client optimization
- **Bean validation removal**: Reduced per-request overhead

## 📁 Project Structure

```
1mrc/
├── README.md                    # This file
├── CLAUDE.md                    # Project instructions  
├── logs/                        # Test execution logs
├── go-service/                  # Go implementation
│   ├── main.go                  # HTTP server
│   ├── test_client.go          # Load test client
│   ├── go.mod                  # Go module  
│   └── README.md               # Go-specific docs
└── java-spring/                # Java Spring Boot implementation
    ├── pom.xml                 # Maven configuration
    ├── src/main/java/...       # Server implementation
    ├── src/test/java/...       # Load test client
    └── README.md               # Java-specific docs
```

## 🎖️ Challenge Results

### Success Criteria
- ✅ **1,000,000 requests processed** with zero data loss
- ✅ **Thread-safe operations** under maximum concurrency  
- ✅ **Performance benchmarks** meeting target thresholds
- ✅ **Resource efficiency** within reasonable limits

### Performance Targets
- **Minimum**: 1,000 requests/second
- **Good**: 10,000 requests/second  
- **Excellent**: 50,000+ requests/second
- **Outstanding**: 100,000+ requests/second

## 🤝 Contributing

Interested in adding more implementations? We welcome:

- **Additional languages**: Python, Rust, Node.js, C#, etc.
- **Different frameworks**: Express, FastAPI, Actix-web, ASP.NET
- **Optimization techniques**: Custom implementations, async patterns
- **Deployment strategies**: Docker, Kubernetes, cloud-native approaches

## 📜 License

This project is part of the One Million Request Challenge (1MRC) demonstration and is available for educational and benchmarking purposes.

---

**Ready to take the challenge?** Pick your implementation and see how your system handles 1 million requests! 🚀