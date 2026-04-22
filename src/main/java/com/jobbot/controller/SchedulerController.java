package com.jobbot.controller;

import com.jobbot.service.SchedulerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/scheduler")
@CrossOrigin(origins = "*")
public class SchedulerController {
    private static final Logger logger = LoggerFactory.getLogger(SchedulerController.class);

    @Autowired
    private SchedulerService schedulerService;

    @PostMapping("/run")
    public ResponseEntity<?> manualRun(@RequestParam Long userId) {
        try {
            logger.info("Manual run triggered for user: {}", userId);
            Map<String, Object> result = schedulerService.executeRun(userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error in manual run", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/start")
    public ResponseEntity<?> startScheduler(@RequestParam Long userId) {
        try {
            logger.info("Scheduler start requested for user: {}", userId);
            return ResponseEntity.ok(Map.of("status", "started", "message", "Hourly scheduler will start in Phase 4"));
        } catch (Exception e) {
            logger.error("Error starting scheduler", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stopScheduler(@RequestParam Long userId) {
        try {
            logger.info("Scheduler stop requested for user: {}", userId);
            return ResponseEntity.ok(Map.of("status", "stopped"));
        } catch (Exception e) {
            logger.error("Error stopping scheduler", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
