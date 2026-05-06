package com.jobbot.service;

import com.jobbot.entity.Application;
import com.jobbot.entity.Job;
import com.jobbot.entity.Resume;
import com.jobbot.entity.UserConfig;
import com.jobbot.exception.LaTeXCompilationException;
import com.jobbot.exception.LoginFailedException;
import com.jobbot.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class ApplicationSubmitter {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationSubmitter.class);

    @Autowired private LaTeXCompiler laTeXCompiler;
    @Autowired private ApplicationRepository applicationRepository;

    public Application submit(Job job, Resume tailoredResume, UserConfig config) {
        // Guard 1: already applied?
        Optional<Application> existing = applicationRepository.findByJob(job);
        if (existing.isPresent()) {
            logger.info("Already applied to job {} — skipping", job.getId());
            return existing.get();
        }

        // Guard 2: not Easy Apply?
        if (!"easy_apply".equals(job.getApplicationType())) {
            Application skipped = new Application();
            skipped.setJob(job);
            skipped.setStatus("skipped");
            skipped.setCreatedAt(LocalDateTime.now());
            applicationRepository.save(skipped);
            logger.info("Job {} is not Easy Apply — skipped", job.getId());
            return skipped;
        }

        // Create pending application record immediately
        Application application = new Application();
        application.setJob(job);
        application.setUsedResume(tailoredResume);
        application.setStatus("pending");
        application.setCreatedAt(LocalDateTime.now());
        applicationRepository.save(application);

        PlaywrightApplicationSession session = null;
        try {
            // Step 1: Compile PDF
            String pdfPath = laTeXCompiler.compileToPdf(tailoredResume.getLatexContent(), job.getId());
            application.setGeneratedPdfPath(pdfPath);

            // Step 2: Login + submit
            session = new PlaywrightApplicationSession();
            session.login(config.getLinkedInEmail(), config.getLinkedInPasswordEncrypted());

            // Use cover letter from application if set, else null
            String coverLetter = application.getCoverLetter();
            boolean submitted = session.submitEasyApply(job.getUrl(), pdfPath, coverLetter);

            application.setStatus(submitted ? "success" : "failed");
            if (submitted) {
                application.setSubmittedAt(LocalDateTime.now());
                logger.info("Successfully applied to job {} at {}", job.getId(), job.getCompany());
            } else {
                application.setErrorReason("Easy Apply form could not be completed");
                logger.warn("Failed to complete Easy Apply for job {}", job.getId());
            }

        } catch (LaTeXCompilationException e) {
            application.setStatus("failed");
            application.setErrorReason("PDF compilation failed: " + e.getMessage());
            logger.error("PDF compilation failed for job {}", job.getId(), e);

        } catch (LoginFailedException e) {
            application.setStatus("failed");
            application.setErrorReason("LinkedIn login failed: " + e.getMessage());
            logger.error("Login failed during application for job {}", job.getId(), e);

        } catch (Exception e) {
            application.setStatus("failed");
            application.setErrorReason(e.getMessage());
            logger.error("Unexpected error applying to job {}", job.getId(), e);

        } finally {
            if (session != null) session.closeSession();
        }

        applicationRepository.save(application);
        return application;
    }
}
