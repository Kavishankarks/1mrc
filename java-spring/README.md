# Java Spring Boot - One Million Request Challenge (1MRC)

Enterprise-grade Spring Boot implementation for handling 1 million concurrent requests with high performance and reliability.

## Features

- **Thread-safe in-memory storage** using `AtomicLong`, `DoubleAdder`, and `ConcurrentHashMap`
- **High-performance Tomcat configuration** with 1000 max threads and 10K max connections
- **Jakarta Bean Validation** for request validation
- **Spring Boot Actuator** for monitoring and health checks
- **Comprehensive logging** with timestamped log files
- **Lock-free operations** for maximum throughput

## Prerequisites

- Java 17 or higher
- Maven 3.6+

## Quick Start

```bash
# Build and run the server
mvn spring-boot:run

# In another terminal, run the load test
mvn exec:java -Dexec.mainClass="com.example.onemrc.LoadTestClient"
```

## API Endpoints

### Core Endpoints
- `POST /event` - Submit events
  ```json
  {
    "userId": "user_123",
    "value": 42.5
  }
  ```

- `GET /stats` - Get aggregated statistics
  ```json
  {
    "totalRequests": 1000000,
    "uniqueUsers": 75000,
    "sum": 499500000.0,
    "avg": 499.5
  }
  ```

- `POST /reset` - Reset all counters (useful for testing)

### Monitoring Endpoints
- `GET /actuator/health` - Application health status
- `GET /actuator/info` - Application information
- `GET /actuator/metrics` - Application metrics

## Performance Configuration

### Tomcat Settings
```yaml
server:
  tomcat:
    threads:
      max: 1000        # Maximum worker threads
      min-spare: 100   # Minimum spare threads
    max-connections: 10000     # Maximum concurrent connections
    accept-count: 1000         # Queue size for incoming connections
    connection-timeout: 20000  # Connection timeout (20 seconds)
```

### JVM Tuning (Recommended)
For optimal performance with 1M requests, use these JVM options:
```bash
export MAVEN_OPTS="-Xmx4g -Xms4g -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+DisableExplicitGC -XX:+UseStringDeduplication"
mvn spring-boot:run
```

Alternative for maximum performance:
```bash
export MAVEN_OPTS="-Xmx4g -Xms4g -XX:+UseParallelGC -XX:ParallelGCThreads=8 -XX:+AggressiveOpts -XX:+UseFastAccessorMethods"
mvn spring-boot:run
```

## Architecture

### Core Components

1. **EventStorageService** - Thread-safe storage layer
   - `AtomicLong` for request counting
   - `DoubleAdder` for high-performance sum aggregation
   - `ConcurrentHashMap` for unique user tracking

2. **EventController** - REST API layer
   - Request validation with Jakarta Bean Validation
   - Error handling and HTTP status management
   - Clean separation of concerns

3. **DTOs** - Data Transfer Objects
   - `EventDto` - Request payload validation
   - `StatsDto` - Response data structure

### Performance Optimizations

- **Lock-free data structures** prevent contention bottlenecks
- **DoubleAdder** provides better performance than AtomicDouble for high-contention scenarios
- **ConcurrentHashMap** offers efficient concurrent user tracking
- **Connection pooling** in test client for realistic load simulation

## Load Testing

The included `LoadTestClient` provides comprehensive performance testing:

### Test Configuration
- **Requests**: 1,000,000 total
- **Concurrency**: 1,000 concurrent workers
- **Test Pattern**: 75,000 unique users with varying values
- **Logging**: Detailed timestamped logs in `logs/` directory

### Test Execution
```bash
mvn exec:java -Dexec.mainClass="com.example.onemrc.LoadTestClient"
```

### Expected Performance
- **Throughput**: 10,000+ requests per second (varies by hardware)
- **Memory Usage**: Low and stable (no memory leaks)
- **Accuracy**: 100% data integrity with atomic operations

## Monitoring and Observability

### Health Checks
```bash
curl http://localhost:8080/actuator/health
```

### Application Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

### Custom Logging
- Application logs: Console output with structured format
- Test logs: `logs/1mrc_java_test_YYYYMMDD_HHMMSS.log`
- Error tracking: Individual worker error logging

## Development

### Project Structure
```
src/
├── main/java/com/example/onemrc/
│   ├── OneMrcApplication.java          # Main Spring Boot application
│   ├── controller/EventController.java # REST API endpoints
│   ├── service/EventStorageService.java # Thread-safe storage
│   └── dto/                           # Data Transfer Objects
│       ├── EventDto.java
│       └── StatsDto.java
├── main/resources/
│   └── application.yml                # Configuration
└── test/java/com/example/onemrc/
    └── LoadTestClient.java           # Performance test client
```

### Building
```bash
# Compile and package
mvn clean package

# Run tests
mvn test

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=production
```

## Troubleshooting

### Common Issues

1. **Port Already in Use**
   ```bash
   # Check what's using port 8080
   lsof -i :8080
   
   # Kill the process
   kill -9 <PID>
   ```

2. **OutOfMemoryError**
   ```bash
   # Increase heap size
   export MAVEN_OPTS="-Xmx4g"
   mvn spring-boot:run
   ```

3. **Connection Refused**
   ```bash
   # Verify server is running
   curl http://localhost:8080/actuator/health
   ```

### Performance Tuning

For production deployments:
1. Adjust `server.tomcat.threads.max` based on CPU cores
2. Monitor GC performance and tune accordingly
3. Use connection pooling in clients
4. Consider horizontal scaling with load balancer

## Comparison with Go Implementation

| Metric | Java Spring Boot | Go |
|--------|------------------|-----|
| Startup Time | ~1 second | Instant |
| Memory Usage | Higher (JVM overhead) | Lower |
| Throughput | 10K-20K req/s | 85K+ req/s |
| Ecosystem | Enterprise features | Lightweight |
| Monitoring | Built-in Actuator | Custom implementation |

Both implementations provide thread-safe, high-performance solutions suitable for the 1MRC challenge.

## License

This implementation is part of the One Million Request Challenge (1MRC) demonstration project.