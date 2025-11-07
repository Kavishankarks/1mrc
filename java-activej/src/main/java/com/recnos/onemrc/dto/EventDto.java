package com.recnos.onemrc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EventDto(
    @JsonProperty("userId") String userId,
    @JsonProperty("value") int value
) {
}