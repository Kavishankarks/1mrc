package org.example;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe accumulator for request statistics.
 */
class StatsService {
    private final Set<String> uniqueUsers = ConcurrentHashMap.newKeySet();
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder sum = new LongAdder();

    void recordEvent(String userId, int value) {
        uniqueUsers.add(userId);
        totalRequests.increment();
        sum.add(value);
    }

    StatsSnapshot snapshot() {
        int requests = totalRequests.intValue();
        int totalSum = sum.intValue();
        double average = requests > 0 ? (double) totalSum / requests : 0.0;
        return new StatsSnapshot(requests, totalSum, uniqueUsers.size(), average);
    }
}
