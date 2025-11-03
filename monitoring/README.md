# 1MRC Monitoring Stack

Global monitoring setup for the One Million Request Challenge (1MRC) using Prometheus and Grafana.

## Overview

This monitoring stack provides real-time observability for all Java service implementations (ActiveJ, Helidon, Netty, Vert.x) and can be extended to monitor Go, Rust, and other services.

## Features

- **Multi-service monitoring**: Compare performance across all implementations
- **Real-time dashboards**: Auto-refreshing metrics every 5 seconds
- **Service filtering**: View all services or filter by specific implementation
- **Comprehensive metrics**:
  - Request rate and latency percentiles (p50, p95, p99)
  - Event processing throughput
  - Error rates (4xx, 5xx)
  - Unique user tracking
  - JVM metrics (heap memory, GC, threads)
  - In-flight request monitoring

## Prerequisites

- Docker and Docker Compose installed
- At least one Java service running with Prometheus metrics enabled

## Quick Start

### 1. Start the Monitoring Stack

```bash
cd monitoring
docker-compose up -d
```

This will start:
- **Prometheus** on http://localhost:9090
- **Grafana** on http://localhost:3000

### 2. Access Grafana

1. Open http://localhost:3000 in your browser
2. Login with default credentials:
   - Username: `admin`
   - Password: `admin`
3. The dashboard "1MRC - One Million Request Challenge" will be auto-provisioned

### 3. Start Your Services

Make sure your Java services are running on the configured ports:

```bash
# ActiveJ
cd java-activej
mvn clean package
java -jar target/onemrc-activej-1.0.0.jar
# Runs on port 8080

# Helidon (if metrics are integrated)
cd java-helidon
# Run on port 8081

# Netty (if metrics are integrated)
cd java-netty
# Run on port 8082

# Vert.x (if metrics are integrated)
cd java-vertx
# Run on port 8083
```

### 4. View Metrics

The Grafana dashboard will automatically start showing metrics as soon as services are running and receiving requests.

## Dashboard Overview

### Panels

1. **Request Rate**: Shows requests/sec for each service
2. **Request Latency Percentiles**: p50, p95, p99 latencies by service
3. **Total Requests**: Aggregate count across all services
4. **Events Processed**: Total events processed
5. **Unique Users**: Current unique user count
6. **Failed Events**: Count of failed event processing
7. **Event Processing Rate**: Events/sec by service
8. **Unique Users Over Time**: User growth tracking
9. **JVM Heap Memory Usage**: Memory consumption by service
10. **Requests In Flight**: Current concurrent requests
11. **Error Rate**: 4xx and 5xx error percentages

### Service Filter

Use the **Service** dropdown at the top to:
- View all services (select "All")
- Compare specific implementations (select multiple)
- Focus on a single service

## Prometheus Queries

Access Prometheus directly at http://localhost:9090 and try these queries:

### Request Rate by Service
```promql
rate(http_requests_total{endpoint="/event",status="200"}[1m])
```

### P99 Latency
```promql
histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket{endpoint="/event"}[5m])) by (le, implementation))
```

### Event Processing Rate
```promql
rate(events_processed_total[1m])
```

### Memory Usage
```promql
jvm_memory_used_bytes{area="heap"}
```

### Error Rate
```promql
sum(rate(http_requests_total{status=~"5.."}[1m])) / sum(rate(http_requests_total[1m])) * 100
```

## Adding New Services

To monitor additional services:

1. **Add Prometheus metrics** to your service (see java-activej as reference)

2. **Update Prometheus configuration** (`prometheus/prometheus.yml`):

```yaml
- job_name: 'my-new-service'
  metrics_path: '/metrics'
  static_configs:
    - targets: ['host.docker.internal:8090']
      labels:
        service: 'myservice'
        implementation: 'MyFramework'
        language: 'java'
        challenge: '1mrc'
```

3. **Restart Prometheus**:

```bash
docker-compose restart prometheus
```

The dashboard will automatically pick up the new service.

## Port Configuration

Default ports configured in `prometheus/prometheus.yml`:

| Service | Port | Status |
|---------|------|--------|
| java-activej | 8080 | ✅ Metrics integrated |
| java-helidon | 8081 | 🔄 Pending |
| java-netty | 8082 | 🔄 Pending |
| java-vertx | 8083 | 🔄 Pending |
| go-service | 8084 | 🔄 Pending |
| rust-service | 8085 | 🔄 Pending |

To change ports, edit `prometheus/prometheus.yml` and restart the stack.

## Running a Load Test

To see the dashboard in action during the 1M request challenge:

```bash
# Run your load test tool
# Example with a simple script:
for i in {1..1000000}; do
  curl -X POST http://localhost:8080/event \
    -H "Content-Type: application/json" \
    -d "{\"userId\":\"user-$((RANDOM%75000))\",\"value\":$((RANDOM%1000))}"
done
```

Watch the dashboard update in real-time showing:
- Request throughput spikes
- Latency changes
- Memory usage patterns
- Unique user accumulation

## Stopping the Stack

```bash
cd monitoring
docker-compose down
```

To remove all data (volumes):

```bash
docker-compose down -v
```

## Troubleshooting

### Services not showing up in Grafana

1. Check if Prometheus is scraping successfully:
   - Go to http://localhost:9090/targets
   - Verify all targets are "UP"

2. Check service is exposing metrics:
   ```bash
   curl http://localhost:8080/metrics
   ```

3. Verify service is running on correct port:
   ```bash
   netstat -an | grep 8080
   ```

### "No data" in panels

- Wait 15-30 seconds for first scrape
- Ensure services are receiving requests
- Check time range (top right of dashboard)

### Prometheus can't reach services

On **macOS/Windows**: Prometheus runs in Docker and uses `host.docker.internal` to reach host services.

On **Linux**: Change `host.docker.internal` to `172.17.0.1` in `prometheus/prometheus.yml`:

```yaml
targets: ['172.17.0.1:8080']
```

## Customization

### Adjusting Scrape Interval

Edit `prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s  # Change to 5s for faster updates
```

### Dashboard Modifications

1. Edit dashboard in Grafana UI
2. Export JSON: Dashboard Settings → JSON Model
3. Save to `grafana/provisioning/dashboards/1mrc-dashboard.json`
4. Restart Grafana: `docker-compose restart grafana`

### Adding Alerts

Create `prometheus/alerts.yml`:

```yaml
groups:
  - name: 1mrc_alerts
    rules:
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[1m]) > 10
        for: 1m
        annotations:
          summary: "High error rate detected"
```

## Advanced Configuration

### Persistent Storage

Data is stored in Docker volumes:
- `prometheus-data`: Time series data
- `grafana-data`: Dashboards and settings

### Data Retention

Default: 15 days. To change, edit `docker-compose.yml`:

```yaml
command:
  - '--storage.tsdb.retention.time=30d'
```

### External Access

To access from other machines, bind to `0.0.0.0` in `docker-compose.yml`:

```yaml
ports:
  - "0.0.0.0:9090:9090"
  - "0.0.0.0:3000:3000"
```

## Metrics Reference

### HTTP Metrics

- `http_requests_total`: Counter of all HTTP requests
- `http_request_duration_seconds`: Histogram of request durations
- `http_requests_in_flight`: Gauge of concurrent requests

### Event Processing Metrics

- `events_processed_total`: Counter of successful events
- `events_failed_total`: Counter of failed events by reason
- `event_processing_duration_seconds`: Histogram of processing time
- `event_values_sum_total`: Sum of all event values
- `event_value_distribution`: Summary with quantiles

### Business Metrics

- `unique_users_total`: Gauge of unique user count

### JVM Metrics (from Prometheus hotspot exporter)

- `jvm_memory_used_bytes`: Memory usage by area
- `jvm_gc_collection_seconds`: GC collection time
- `jvm_threads_current`: Current thread count
- And many more...

## Resources

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [PromQL Basics](https://prometheus.io/docs/prometheus/latest/querying/basics/)

## Support

For issues or questions about the monitoring setup, please check:
1. Service logs: `docker-compose logs prometheus` or `docker-compose logs grafana`
2. Prometheus targets: http://localhost:9090/targets
3. Service metrics endpoint: http://localhost:8080/metrics