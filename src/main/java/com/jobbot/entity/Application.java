package com.jobbot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne
    @JoinColumn(name = "resume_id")
    private Resume usedResume;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String errorReason;

    @Column(columnDefinition = "TEXT")
    private String generatedPdfPath;

    @Column(columnDefinition = "TEXT")
    private String coverLetter;

    @Column(columnDefinition = "TEXT")
    private String resumeVersionHash;

    @Column(columnDefinition = "TEXT")
    private String applicationResponse;

    @Column
    private LocalDateTime submittedAt;

    @Column
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }
    public Resume getUsedResume() { return usedResume; }
    public void setUsedResume(Resume resume) { this.usedResume = resume; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String reason) { this.errorReason = reason; }
    public String getGeneratedPdfPath() { return generatedPdfPath; }
    public void setGeneratedPdfPath(String path) { this.generatedPdfPath = path; }
    public String getCoverLetter() { return coverLetter; }
    public void setCoverLetter(String letter) { this.coverLetter = letter; }
    public String getResumeVersionHash() { return resumeVersionHash; }
    public void setResumeVersionHash(String hash) { this.resumeVersionHash = hash; }
    public String getApplicationResponse() { return applicationResponse; }
    public void setApplicationResponse(String response) { this.applicationResponse = response; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime at) { this.submittedAt = at; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime at) { this.createdAt = at; }
}
