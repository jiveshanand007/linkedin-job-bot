package com.jobbot.controller;

import com.jobbot.dto.SearchConfigRequest;
import com.jobbot.dto.SearchConfigResponse;
import com.jobbot.entity.SearchConfig;
import com.jobbot.entity.UserConfig;
import com.jobbot.repository.SearchConfigRepository;
import com.jobbot.repository.UserConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/search-config")
@CrossOrigin(origins = "*")
public class SearchConfigController {

    private static final Logger logger = LoggerFactory.getLogger(SearchConfigController.class);

    @Autowired private SearchConfigRepository searchConfigRepository;
    @Autowired private UserConfigRepository userConfigRepository;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SearchConfigRequest req) {
        try {
            if (req.getUserConfigId() == null) {
                return ResponseEntity.badRequest().body("userConfigId is required");
            }

            UserConfig userConfig = userConfigRepository.findById(req.getUserConfigId())
                .orElse(null);
            if (userConfig == null) {
                return ResponseEntity.notFound().build();
            }

            if (searchConfigRepository.findByUserConfig(userConfig).isPresent()) {
                return ResponseEntity.status(409).body("SearchConfig already exists for this user");
            }

            SearchConfig config = new SearchConfig();
            config.setUserConfig(userConfig);
            applyRequest(config, req);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());

            SearchConfig saved = searchConfigRepository.save(config);
            logger.info("SearchConfig created for user {}", req.getUserConfigId());
            return ResponseEntity.status(201).body(toResponse(saved));

        } catch (Exception e) {
            logger.error("Error creating SearchConfig", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SearchConfigRequest req) {
        try {
            SearchConfig config = searchConfigRepository.findById(id).orElse(null);
            if (config == null) return ResponseEntity.notFound().build();

            // Full replace: null fields reset to defaults
            applyRequest(config, req);
            config.setUpdatedAt(LocalDateTime.now());

            SearchConfig saved = searchConfigRepository.save(config);
            return ResponseEntity.ok(toResponse(saved));

        } catch (Exception e) {
            logger.error("Error updating SearchConfig {}", id, e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/user/{userConfigId}")
    public ResponseEntity<?> getByUser(@PathVariable Long userConfigId) {
        try {
            UserConfig userConfig = userConfigRepository.findById(userConfigId).orElse(null);
            if (userConfig == null) return ResponseEntity.notFound().build();

            return searchConfigRepository.findByUserConfig(userConfig)
                .map(c -> ResponseEntity.ok((Object) toResponse(c)))
                .orElse(ResponseEntity.notFound().build());

        } catch (Exception e) {
            logger.error("Error fetching SearchConfig for user {}", userConfigId, e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            if (!searchConfigRepository.existsById(id)) return ResponseEntity.notFound().build();
            searchConfigRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting SearchConfig {}", id, e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // Applies request fields to entity; null = reset to default
    private void applyRequest(SearchConfig config, SearchConfigRequest req) {
        config.setRemoteOnly(req.getRemoteOnly() != null ? req.getRemoteOnly() : false);
        config.setExperienceLevel(req.getExperienceLevel()); // null = any
        config.setDatePostedFilter(req.getDatePostedFilter() != null ? req.getDatePostedFilter() : "ANY");
        config.setMaxPages(req.getMaxPages() != null ? req.getMaxPages() : 3);
    }

    private SearchConfigResponse toResponse(SearchConfig config) {
        SearchConfigResponse resp = new SearchConfigResponse();
        resp.setId(config.getId());
        resp.setUserConfigId(config.getUserConfig().getId());
        resp.setRemoteOnly(config.getRemoteOnly());
        resp.setExperienceLevel(config.getExperienceLevel());
        resp.setDatePostedFilter(config.getDatePostedFilter());
        resp.setMaxPages(config.getMaxPages());
        resp.setCreatedAt(config.getCreatedAt());
        resp.setUpdatedAt(config.getUpdatedAt());
        return resp;
    }
}
