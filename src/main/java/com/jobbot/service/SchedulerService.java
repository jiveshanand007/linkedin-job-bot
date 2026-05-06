package com.jobbot.service;

import com.jobbot.entity.Job;
import com.jobbot.entity.Resume;
import com.jobbot.entity.UserConfig;
import com.jobbot.repository.JobRepository;
import com.jobbot.repository.ResumeRepository;
import com.jobbot.repository.UserConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeTailor resumeTailor;

    public Map<String, Object> executeRun(Long userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            UserConfig config = userConfigRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User config not found"));

            logger.info("Starting job search for user: {}", userId);

            List<Job> fetchedJobs = jobFetcher.fetchJobs(config);

            List<Job> matchedJobs = jobMatcher.filterJobs(fetchedJobs, config);
            matchedJobs.forEach(jobRepository::save);

            Optional<Resume> activeResume = resumeRepository.findFirstByUserConfigAndIsActive(config, true);

            int tailoringErrors = 0;
            if (activeResume.isPresent()) {
                for (Job job : matchedJobs) {
                    Optional<Resume> tailored = resumeTailor.tailorAndSave(job, activeResume.get());
                    if (tailored.isEmpty()) tailoringErrors++;
                }
            } else {
                logger.warn("No active base resume for user {} — skipping tailoring", userId);
            }

            result.put("status", "success");
            result.put("jobsFetched", fetchedJobs.size());
            result.put("jobsMatched", matchedJobs.size());
            result.put("applicationsSubmitted", 0);
            result.put("tailoringErrors", tailoringErrors);

            logger.info("Run completed for user {}. Matched {} jobs, {} tailoring errors",
                userId, matchedJobs.size(), tailoringErrors);

        } catch (Exception e) {
            logger.error("Error executing bot run", e);
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }

        return result;
    }
}

