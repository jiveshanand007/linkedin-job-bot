package com.jobbot.controller;

import com.jobbot.entity.Job;
import com.jobbot.entity.Resume;
import com.jobbot.repository.JobRepository;
import com.jobbot.repository.ResumeRepository;
import com.jobbot.service.ResumeTailor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/resumes")
@CrossOrigin(origins = "*")
public class ResumeController {

    private static final Logger logger = LoggerFactory.getLogger(ResumeController.class);

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeTailor resumeTailor;

    @PostMapping("/tailor")
    public ResponseEntity<?> tailorResume(
            @RequestParam Long resumeId,
            @RequestParam Long jobId) {

        Optional<Resume> baseResumeOpt = resumeRepository.findById(resumeId);
        if (baseResumeOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Resume not found: " + resumeId));
        }

        Optional<Job> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Job not found: " + jobId));
        }

        Optional<Resume> tailored = resumeTailor.tailorAndSave(jobOpt.get(), baseResumeOpt.get());
        if (tailored.isEmpty()) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Tailoring failed — check logs"));
        }

        Resume r = tailored.get();
        return ResponseEntity.ok(Map.of(
            "resumeId", r.getId(),
            "versionName", r.getVersionName(),
            "latexContent", r.getLatexContent()
        ));
    }
}
