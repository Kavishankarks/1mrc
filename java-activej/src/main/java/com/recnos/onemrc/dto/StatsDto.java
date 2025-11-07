package com.recnos.onemrc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StatsDto(
    @JsonProperty("totalRequests") long totalRequests,
    @JsonProperty("uniqueUsers") long uniqueUsers,
    @JsonProperty("sum") long sum,
    @JsonProperty("avg") double avg
) {
}