package com.recnos.onemrc.controller;

import com.recnos.onemrc.dto.EventDto;
import com.recnos.onemrc.dto.StatsDto;
import com.recnos.onemrc.service.EventStorageService;
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
    public ResponseEntity<StatsDto> getStats() {
        StatsDto stats = eventStorageService.getStats();
        return ResponseEntity.ok(stats);
    }
    
    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        eventStorageService.reset();
        return ResponseEntity.ok().build();
    }
}