package com.recnos.onemrc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventDto {
    
    @JsonProperty("userId")
    @NotBlank(message = "userId is required")
    private String userId;
    
    @JsonProperty("value")
    @NotNull(message = "value is required")
    private Double value;

    @Override
    public String toString() {
        return String.format("EventDto{userId='%s', value=%s}", userId, value);
    }
}