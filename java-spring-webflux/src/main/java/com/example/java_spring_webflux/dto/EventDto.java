package com.example.java_spring_webflux.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventDto {
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("value")
    private Double value;

    @Override
    public String toString() {
        return String.format("EventDto{userId='%s', value=%s}", userId, value);
    }
}