package com.jobbot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resumes")
public class Resume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_config_id", nullable = false)
    private UserConfig userConfig;

    @Column(nullable = false)
    private String versionName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String latexContent;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column
    private LocalDateTime uploadedAt;

    @Column
    private LocalDateTime updatedAt;

    @Column
    private Long parentResumeId;

    @Column
    private Long jobId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UserConfig getUserConfig() { return userConfig; }
    public void setUserConfig(UserConfig config) { this.userConfig = config; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String name) { this.versionName = name; }
    public String getLatexContent() { return latexContent; }
    public void setLatexContent(String content) { this.latexContent = content; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { this.isActive = active; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime at) { this.uploadedAt = at; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime at) { this.updatedAt = at; }
    public Long getParentResumeId() { return parentResumeId; }
    public void setParentResumeId(Long id) { this.parentResumeId = id; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long id) { this.jobId = id; }
}
