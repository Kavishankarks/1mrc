package com.recnos.onemrc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatsDto {
    
    @JsonProperty("totalRequests")
    private long totalRequests;
    
    @JsonProperty("uniqueUsers")
    private long uniqueUsers;
    
    @JsonProperty("sum")
    private double sum;
    
    @JsonProperty("avg")
    private double avg;
    
    @Override
    public String toString() {
        return String.format("StatsDto{totalRequests=%d, uniqueUsers=%d, sum=%.2f, avg=%.2f}", 
                           totalRequests, uniqueUsers, sum, avg);
    }
}