package com.jobbot.dto;

import java.time.LocalDateTime;

public class ApplicationResponse {
    private Long id;
    private Long jobId;
    private String jobTitle;
    private String company;
    private String status;
    private String errorReason;
    private String generatedPdfPath;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;

    public ApplicationResponse() {
    }

    public ApplicationResponse(Long id, Long jobId, String jobTitle, String company, String status,
                              String errorReason, String generatedPdfPath, LocalDateTime submittedAt,
                              LocalDateTime createdAt) {
        this.id = id;
        this.jobId = jobId;
        this.jobTitle = jobTitle;
        this.company = company;
        this.status = status;
        this.errorReason = errorReason;
        this.generatedPdfPath = generatedPdfPath;
        this.submittedAt = submittedAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
    }

    public String getGeneratedPdfPath() {
        return generatedPdfPath;
    }

    public void setGeneratedPdfPath(String generatedPdfPath) {
        this.generatedPdfPath = generatedPdfPath;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
