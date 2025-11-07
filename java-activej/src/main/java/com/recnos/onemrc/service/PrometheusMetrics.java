package com.recnos.onemrc.service;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import io.prometheus.client.hotspot.DefaultExports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized Prometheus metrics for the One Million Request Challenge.
 *
 * This service provides thread-safe metrics collection for:
 * - HTTP request/response metrics
 * - Event processing metrics
 * - Business metrics (users, values)
 * - JVM metrics (memory, GC, threads)
 */
public class PrometheusMetrics {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusMetrics.class);
    private static final PrometheusMetrics INSTANCE = new PrometheusMetrics();

    // HTTP Request Metrics
    public final Counter httpRequestsTotal = Counter.build()
            .name("http_requests_total")
            .help("Total number of HTTP requests")
            .labelNames("method", "endpoint", "status")
            .register();

    public final Histogram httpRequestDuration = Histogram.build()
            .name("http_request_duration_seconds")
            .help("HTTP request duration in seconds")
            .labelNames("method", "endpoint")
            .buckets(0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0)
            .register();

    public final Gauge httpRequestsInFlight = Gauge.build()
            .name("http_requests_in_flight")
            .help("Number of HTTP requests currently being processed")
            .labelNames("method", "endpoint")
            .register();

    // Event Processing Metrics
    public final Counter eventsProcessedTotal = Counter.build()
            .name("events_processed_total")
            .help("Total number of events processed")
            .register();

    public final Counter eventsFailedTotal = Counter.build()
            .name("events_failed_total")
            .help("Total number of events that failed to process")
            .labelNames("reason")
            .register();

    public final Histogram eventProcessingDuration = Histogram.build()
            .name("event_processing_duration_seconds")
            .help("Event processing duration in seconds")
            .buckets(0.0001, 0.0005, 0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1)
            .register();

    // Business Metrics
    public final Gauge uniqueUsersTotal = Gauge.build()
            .name("unique_users_total")
            .help("Total number of unique users")
            .register();

    public final Counter eventValueSum = Counter.build()
            .name("event_values_sum_total")
            .help("Sum of all event values")
            .register();

    public final Summary eventValueSummary = Summary.build()
            .name("event_value_distribution")
            .help("Distribution of event values with quantiles")
            .quantile(0.5, 0.05)   // 50th percentile (median) with 5% error
            .quantile(0.75, 0.025) // 75th percentile with 2.5% error
            .quantile(0.95, 0.01)  // 95th percentile with 1% error
            .quantile(0.99, 0.001) // 99th percentile with 0.1% error
            .register();

    // System Metrics
    public final Gauge memoryUsedBytes = Gauge.build()
            .name("jvm_memory_used_bytes")
            .help("JVM memory used in bytes")
            .labelNames("area")
            .register();

    private PrometheusMetrics() {
        // Initialize default JVM metrics (GC, memory pools, threads, etc.)
        DefaultExports.initialize();
        logger.info("PrometheusMetrics initialized with default JVM metrics");
        logger.info("Metrics available at /metrics endpoint");
    }

    public static PrometheusMetrics getInstance() {
        return INSTANCE;
    }

    /**
     * Records an HTTP request with timing and status.
     */
    public void recordHttpRequest(String method, String endpoint, int status, double durationSeconds) {
        httpRequestsTotal.labels(method, endpoint, String.valueOf(status)).inc();
        httpRequestDuration.labels(method, endpoint).observe(durationSeconds);
    }

    /**
     * Records a successful event processing.
     */
    public void recordEventProcessed(double value, double durationSeconds) {
        eventsProcessedTotal.inc();
        eventValueSum.inc(value);
        eventValueSummary.observe(value);
        eventProcessingDuration.observe(durationSeconds);
    }

    /**
     * Records a failed event processing.
     */
    public void recordEventFailed(String reason) {
        eventsFailedTotal.labels(reason).inc();
    }

    /**
     * Updates the unique users gauge.
     */
    public void updateUniqueUsers(long count) {
        uniqueUsersTotal.set(count);
    }

    /**
     * Updates memory usage metrics.
     */
    public void updateMemoryMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        memoryUsedBytes.labels("heap").set(usedMemory);
        memoryUsedBytes.labels("max").set(maxMemory);
    }
}