package com.example.java_spring_webflux.service;

import com.example.java_spring_webflux.dto.EventDto;
import com.example.java_spring_webflux.dto.StatsDto;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

@Service
public class EventStorageService {

  private final AtomicLong totalRequests = new AtomicLong(0);
  private final DoubleAdder sum = new DoubleAdder();
  private final ConcurrentHashMap<String, Boolean> users = new ConcurrentHashMap<>(100000, 0.75f,
      16);

  public void addEvent(EventDto event) {
    // Batch operations for better performance
    totalRequests.incrementAndGet();
    sum.add(event.getValue());

    // Use putIfAbsent for better performance than put
    users.putIfAbsent(event.getUserId(), Boolean.TRUE);
  }

  public StatsDto getStats() {
    long totalReqs = totalRequests.get();
    double sumValue = sum.sum();
    long uniqueUsers = users.size();

    double avg = totalReqs > 0 ? sumValue / totalReqs : 0.0;

    return new StatsDto(totalReqs, uniqueUsers, sumValue, avg);
  }

  public void reset() {
    totalRequests.set(0);
    sum.reset();
    users.clear();
  }
}