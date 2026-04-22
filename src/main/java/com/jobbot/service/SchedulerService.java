package com.jobbot.service;

import com.jobbot.entity.UserConfig;
import com.jobbot.entity.Job;
import com.jobbot.repository.JobRepository;
import com.jobbot.repository.UserConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Service
public class SchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(SchedulerService.class);

    @Autowired
    private LinkedInJobFetcher jobFetcher;

    @Autowired
    private JobMatcher jobMatcher;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserConfigRepository userConfigRepository;

    public Map<String, Object> executeRun(Long userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            UserConfig config = userConfigRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User config not found"));

            logger.info("Starting job search for user: {}", userId);

            // Step 1: Fetch jobs from LinkedIn
            List<Job> fetchedJobs = jobFetcher.searchJobs(
                config,
                config.getJobKeywords(),
                config.getYearsExperienceMax(),
                config.getLocation()
            );

            // Step 2: Filter jobs
            List<Job> matchedJobs = jobMatcher.filterJobs(fetchedJobs, config);

            // Step 3: Save matched jobs to database
            matchedJobs.forEach(job -> jobRepository.save(job));

            result.put("status", "success");
            result.put("jobsFetched", fetchedJobs.size());
            result.put("jobsMatched", matchedJobs.size());
            result.put("applicationsSubmitted", 0);

            logger.info("Run completed for user {}. Matched {} jobs", userId, matchedJobs.size());
        } catch (Exception e) {
            logger.error("Error executing bot run", e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }

        return result;
    }
}
