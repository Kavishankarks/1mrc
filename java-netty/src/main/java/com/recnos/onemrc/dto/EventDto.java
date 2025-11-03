package com.recnos.onemrc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EventDto {

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("value")
    private double value;

    public EventDto() {
    }

    public EventDto(String userId, double value) {
        this.userId = userId;
        this.value = value;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "EventDto{userId='" + userId + "', value=" + value + "}";
    }
}