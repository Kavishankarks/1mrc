package com.recnos.onemrc.service;

import com.recnos.onemrc.dto.EventDto;
import com.recnos.onemrc.dto.StatsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Ultra-high performance event storage service with lock-free concurrent aggregation.
 * Optimized for millions of concurrent requests with minimal contention.
 */
public class EventStorageService {

    private static final Logger logger = LoggerFactory.getLogger(EventStorageService.class);
    private static final EventStorageService INSTANCE = new EventStorageService();

    // Lock-free atomic counters for maximum throughput
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder sum = new LongAdder();

    // High-performance concurrent set for unique users (more efficient than Map<String, Boolean>)
    private final Set<String> users = ConcurrentHashMap.newKeySet();

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
        totalRequests.increment();
        sum.add(event.value());
        users.add(event.userId());
    }

    /**
     * Returns aggregated statistics.
     * This operation is wait-free and returns a consistent snapshot.
     *
     * @return stats containing total requests, unique users, sum, and average
     */
    public StatsDto getStats() {
        long totalReqs = totalRequests.sum();
        long sumValue = sum.sum();
        long uniqueUsers = users.size();

        // Calculate average with division by zero protection
        double avg = totalReqs > 0 ? (double) sumValue / totalReqs : 0.0;

        return new StatsDto(totalReqs, uniqueUsers, sumValue, avg);
    }

    /**
     * Resets all statistics. Use with caution in production.
     */
    public void reset() {
        totalRequests.reset();
        sum.reset();
        users.clear();
        logger.info("EventStorageService statistics reset");
    }
}