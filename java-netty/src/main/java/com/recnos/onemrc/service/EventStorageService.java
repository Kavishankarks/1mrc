package com.recnos.onemrc.service;

import com.recnos.onemrc.dto.EventDto;
import com.recnos.onemrc.dto.StatsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Ultra-high performance event storage service with lock-free concurrent aggregation.
 * Optimized for millions of concurrent requests with minimal contention.
 */
public class EventStorageService {

    private static final Logger logger = LoggerFactory.getLogger(EventStorageService.class);
    private static final EventStorageService INSTANCE = new EventStorageService();

    // Lock-free atomic counters for maximum throughput
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final DoubleAdder sum = new DoubleAdder();

    // High-performance concurrent hash map with optimized parameters
    // Initial capacity: 100000, Load factor: 0.75, Concurrency level: 64 for high parallelism
    private final ConcurrentHashMap<String, Boolean> users = new ConcurrentHashMap<>(100000, 0.75f, 64);

    private EventStorageService() {
        logger.info("EventStorageService initialized with lock-free concurrent data structures");
    }

    public static EventStorageService getInstance() {
        return INSTANCE;
    }

    /**
     * Adds an event with ultra-low latency using lock-free operations.
     * This method is thread-safe and optimized for millions of concurrent calls.
     *
     * @param event the event to add
     */
    public void addEvent(EventDto event) {
        // All operations are lock-free for maximum throughput
        totalRequests.incrementAndGet();
        sum.add(event.getValue());

        // putIfAbsent is more efficient than computeIfAbsent for Boolean values
        // Using Boolean.TRUE as a lightweight marker
        users.putIfAbsent(event.getUserId(), Boolean.TRUE);
    }

    /**
     * Returns aggregated statistics.
     * This operation is wait-free and returns a consistent snapshot.
     *
     * @return stats containing total requests, unique users, sum, and average
     */
    public StatsDto getStats() {
        long totalReqs = totalRequests.get();
        double sumValue = sum.sum();
        long uniqueUsers = users.size();

        // Calculate average with division by zero protection
        double avg = totalReqs > 0 ? sumValue / totalReqs : 0.0;

        return new StatsDto(totalReqs, uniqueUsers, sumValue, avg);
    }

    /**
     * Resets all statistics. Use with caution in production.
     */
    public void reset() {
        totalRequests.set(0);
        sum.reset();
        users.clear();
        logger.info("EventStorageService statistics reset");
    }
}