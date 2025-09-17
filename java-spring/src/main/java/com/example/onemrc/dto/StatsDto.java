package com.example.onemrc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StatsDto {
    
    @JsonProperty("totalRequests")
    private long totalRequests;
    
    @JsonProperty("uniqueUsers")
    private long uniqueUsers;
    
    @JsonProperty("sum")
    private double sum;
    
    @JsonProperty("avg")
    private double avg;
    
    public StatsDto() {}
    
    public StatsDto(long totalRequests, long uniqueUsers, double sum, double avg) {
        this.totalRequests = totalRequests;
        this.uniqueUsers = uniqueUsers;
        this.sum = sum;
        this.avg = avg;
    }
    
    public long getTotalRequests() {
        return totalRequests;
    }
    
    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }
    
    public long getUniqueUsers() {
        return uniqueUsers;
    }
    
    public void setUniqueUsers(long uniqueUsers) {
        this.uniqueUsers = uniqueUsers;
    }
    
    public double getSum() {
        return sum;
    }
    
    public void setSum(double sum) {
        this.sum = sum;
    }
    
    public double getAvg() {
        return avg;
    }
    
    public void setAvg(double avg) {
        this.avg = avg;
    }
    
    @Override
    public String toString() {
        return String.format("StatsDto{totalRequests=%d, uniqueUsers=%d, sum=%.2f, avg=%.2f}", 
                           totalRequests, uniqueUsers, sum, avg);
    }
}