package com.jobbot.service;

import com.jobbot.entity.Job;
import com.jobbot.entity.Resume;
import com.jobbot.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ResumeTailor {

    private static final Logger logger = LoggerFactory.getLogger(ResumeTailor.class);

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private ResumeRepository resumeRepository;

    /**
     * Tailors baseResume for the given job, persists the result, and returns it.
     * Returns Optional.empty() on failure — callers use this to detect and count errors.
     */
    public Optional<Resume> tailorAndSave(Job job, Resume baseResume) {
        try {
            String tailoredLatex = claudeApiClient.rewriteResume(
                baseResume.getLatexContent(),
                job.getTitle(),
                job.getCompany(),
                job.getJobDescription()
            );

            Resume tailored = new Resume();
            tailored.setUserConfig(baseResume.getUserConfig());
            tailored.setParentResumeId(baseResume.getId());
            tailored.setJobId(job.getId());
            tailored.setVersionName("tailored-for-" + job.getId());
            tailored.setLatexContent(tailoredLatex);
            tailored.setIsActive(false);
            tailored.setUploadedAt(LocalDateTime.now());
            tailored.setUpdatedAt(LocalDateTime.now());

            Resume saved = resumeRepository.save(tailored);
            logger.info("Tailored resume saved for job {} ({})", job.getId(), job.getTitle());
            return Optional.of(saved);

        } catch (Exception e) {
            logger.error("Failed to tailor resume for job {}: {}", job.getId(), e.getMessage());
            return Optional.empty();
        }
    }
}
