package com.jobbot.controller;

import com.jobbot.entity.UserConfig;
import com.jobbot.entity.Resume;
import com.jobbot.repository.UserConfigRepository;
import com.jobbot.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "*")
public class ConfigController {
    private static final Logger logger = LoggerFactory.getLogger(ConfigController.class);

    @Autowired
    private UserConfigRepository userConfigRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @PostMapping("/setup")
    public ResponseEntity<?> setupConfig(@RequestBody UserConfig config) {
        try {
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            UserConfig saved = userConfigRepository.save(config);
            logger.info("User config created: {}", saved.getId());
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            logger.error("Error setting up config", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/resumes/upload")
    public ResponseEntity<?> uploadResume(@RequestParam Long userId, @RequestBody Map<String, String> payload) {
        try {
            UserConfig config = userConfigRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User config not found"));

            Resume resume = new Resume();
            resume.setUserConfig(config);
            resume.setVersionName(payload.get("versionName"));
            resume.setLatexContent(payload.get("latexContent"));
            resume.setIsActive(true);
            resume.setUploadedAt(LocalDateTime.now());
            resume.setUpdatedAt(LocalDateTime.now());

            Resume saved = resumeRepository.save(resume);
            logger.info("Resume uploaded: {}", saved.getId());
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            logger.error("Error uploading resume", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/resumes/{userId}")
    public ResponseEntity<?> listResumes(@PathVariable Long userId) {
        try {
            UserConfig config = userConfigRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User config not found"));

            List<Resume> resumes = resumeRepository.findByUserConfig(config);
            return ResponseEntity.ok(resumes);
        } catch (Exception e) {
            logger.error("Error listing resumes", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getConfig(@PathVariable Long userId) {
        try {
            UserConfig config = userConfigRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Config not found"));
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            logger.error("Error fetching config", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
