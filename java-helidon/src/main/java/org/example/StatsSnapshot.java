package org.example;

/**
 * Serialisable view of the aggregated statistics.
 */
public record StatsSnapshot(int totalRequests, int sum, int uniqueUsers, double average) {
}
