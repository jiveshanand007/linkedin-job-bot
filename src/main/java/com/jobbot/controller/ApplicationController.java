package com.jobbot.controller;

import com.jobbot.dto.ApplicationResponse;
import com.jobbot.entity.Application;
import com.jobbot.entity.Job;
import com.jobbot.entity.UserConfig;
import com.jobbot.repository.ApplicationRepository;
import com.jobbot.repository.JobRepository;
import com.jobbot.repository.UserConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/applications")
@CrossOrigin(origins = "*")
public class ApplicationController {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationController.class);

    @Autowired
    private UserConfigRepository userConfigRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getApplicationsByUser(@PathVariable Long userId) {
        try {
            Optional<UserConfig> userConfigOpt = userConfigRepository.findById(userId);
            if (!userConfigOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            UserConfig userConfig = userConfigOpt.get();
            List<Job> jobs = jobRepository.findByUserConfig(userConfig);

            List<ApplicationResponse> applications = jobs.stream()
                    .flatMap(job -> applicationRepository.findByJob(job).stream()
                            .map(application -> mapToApplicationResponse(application, job)))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(applications);
        } catch (Exception e) {
            logger.error("Error retrieving applications for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private ApplicationResponse mapToApplicationResponse(Application application, Job job) {
        return new ApplicationResponse(
                application.getId(),
                job.getId(),
                job.getTitle(),
                job.getCompany(),
                application.getStatus(),
                application.getErrorReason(),
                application.getGeneratedPdfPath(),
                application.getSubmittedAt(),
                application.getCreatedAt()
        );
    }
}
