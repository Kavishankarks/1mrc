package com.example.onemrc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class EventDto {
    
    @JsonProperty("userId")
    @NotBlank(message = "userId is required")
    private String userId;
    
    @JsonProperty("value")
    @NotNull(message = "value is required")
    private Double value;
    
    public EventDto() {}
    
    public EventDto(String userId, Double value) {
        this.userId = userId;
        this.value = value;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public Double getValue() {
        return value;
    }
    
    public void setValue(Double value) {
        this.value = value;
    }
    
    @Override
    public String toString() {
        return String.format("EventDto{userId='%s', value=%s}", userId, value);
    }
}