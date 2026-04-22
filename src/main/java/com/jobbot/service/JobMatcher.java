package com.jobbot.service;

import com.jobbot.entity.Job;
import com.jobbot.entity.UserConfig;
import com.jobbot.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class JobMatcher {
    private static final Logger logger = LoggerFactory.getLogger(JobMatcher.class);

    @Autowired
    private ApplicationRepository applicationRepository;

    public List<Job> filterJobs(List<Job> jobs, UserConfig config) {
        List<Job> filtered = new ArrayList<>();
        List<String> requiredKeywords = parseKeywords(config.getJobKeywords());
        List<String> blacklistKeywords = parseKeywords(config.getBlacklistKeywords());

        for (Job job : jobs) {
            // Skip if already applied
            if (applicationRepository.findByJob(job).isPresent()) {
                logger.debug("Skipping job (already applied): {}", job.getLinkedInJobId());
                continue;
            }

            // Check salary
            if (job.getSalary() != null && job.getSalary() < config.getMinSalaryLPA()) {
                logger.debug("Skipping job (low salary): {} LPA for {}", job.getSalary(), job.getTitle());
                continue;
            }

            // Check required keywords
            String jobText = (job.getTitle() + " " + job.getJobDescription()).toLowerCase();
            boolean hasRequiredKeyword = requiredKeywords.stream()
                .anyMatch(kw -> jobText.contains(kw.toLowerCase()));

            if (!hasRequiredKeyword) {
                logger.debug("Skipping job (no required keywords): {}", job.getTitle());
                continue;
            }

            // Check blacklist keywords
            boolean hasBlacklistKeyword = blacklistKeywords.stream()
                .anyMatch(kw -> jobText.contains(kw.toLowerCase()));

            if (hasBlacklistKeyword) {
                logger.debug("Skipping job (blacklist keyword): {}", job.getTitle());
                continue;
            }

            filtered.add(job);
            logger.debug("Job matched: {} at {}", job.getTitle(), job.getCompany());
        }

        logger.info("Filtered {} jobs to {} matches", jobs.size(), filtered.size());
        return filtered;
    }

    private List<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(keywords.split(",\\s*"));
    }
}
