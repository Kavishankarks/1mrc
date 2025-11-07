package com.example.java_spring_webflux.controller;

import com.example.java_spring_webflux.dto.EventDto;
import com.example.java_spring_webflux.dto.StatsDto;
import com.example.java_spring_webflux.service.EventStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class EventController {
    
    private final EventStorageService eventStorageService;
    
    @Autowired
    public EventController(EventStorageService eventStorageService) {
        this.eventStorageService = eventStorageService;
    }

    @PostMapping("/event")
    public ResponseEntity<Void> addEvent(@RequestBody EventDto event) {
        // Quick null check instead of full validation for performance
        if (event == null || event.getValue() == null) {
            return ResponseEntity.badRequest().build();
        }
        eventStorageService.addEvent(event);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/stats")
    public StatsDto getStats() {
        return eventStorageService.getStats();
    }
    
    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        eventStorageService.reset();
        return ResponseEntity.ok(null);
    }
}