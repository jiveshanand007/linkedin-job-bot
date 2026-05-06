package com.jobbot.controller;

import com.jobbot.service.SchedulerService;
import com.jobbot.entity.UserConfig;
import com.jobbot.repository.UserConfigRepository;
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

    @Autowired
    private UserConfigRepository userConfigRepository;

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
            UserConfig config = userConfigRepository.findById(userId).orElse(null);
            if (config == null) return ResponseEntity.notFound().build();
            config.setSchedulerActive(true);
            userConfigRepository.save(config);
            logger.info("Scheduler activated for user {}", userId);
            return ResponseEntity.ok(Map.of("status", "started", "userId", userId));
        } catch (Exception e) {
            logger.error("Error starting scheduler for user {}", userId, e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stopScheduler(@RequestParam Long userId) {
        try {
            UserConfig config = userConfigRepository.findById(userId).orElse(null);
            if (config == null) return ResponseEntity.notFound().build();
            config.setSchedulerActive(false);
            userConfigRepository.save(config);
            logger.info("Scheduler deactivated for user {}", userId);
            return ResponseEntity.ok(Map.of("status", "stopped", "userId", userId));
        } catch (Exception e) {
            logger.error("Error stopping scheduler for user {}", userId, e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestParam Long userId) {
        try {
            UserConfig config = userConfigRepository.findById(userId).orElse(null);
            if (config == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(Map.of(
                "userId", userId,
                "schedulerActive", config.isSchedulerActive(),
                "autoApplyEnabled", Boolean.TRUE.equals(config.getAutoApplyEnabled())
            ));
        } catch (Exception e) {
            logger.error("Error getting scheduler status for user {}", userId, e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
