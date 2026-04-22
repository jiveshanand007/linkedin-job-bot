package com.jobbot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_config")
public class UserConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String linkedInEmail;

    @Column(nullable = false)
    private String linkedInPasswordEncrypted;

    @Column(columnDefinition = "TEXT")
    private String jobKeywords;

    @Column(columnDefinition = "TEXT")
    private String blacklistKeywords;

    @Column(nullable = false)
    private Integer minSalaryLPA;

    @Column(nullable = false)
    private Integer yearsExperienceMax;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private Boolean autoApplyEnabled = false;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getLinkedInEmail() { return linkedInEmail; }
    public void setLinkedInEmail(String linkedInEmail) { this.linkedInEmail = linkedInEmail; }
    public String getLinkedInPasswordEncrypted() { return linkedInPasswordEncrypted; }
    public void setLinkedInPasswordEncrypted(String pwd) { this.linkedInPasswordEncrypted = pwd; }
    public String getJobKeywords() { return jobKeywords; }
    public void setJobKeywords(String keywords) { this.jobKeywords = keywords; }
    public String getBlacklistKeywords() { return blacklistKeywords; }
    public void setBlacklistKeywords(String keywords) { this.blacklistKeywords = keywords; }
    public Integer getMinSalaryLPA() { return minSalaryLPA; }
    public void setMinSalaryLPA(Integer salary) { this.minSalaryLPA = salary; }
    public Integer getYearsExperienceMax() { return yearsExperienceMax; }
    public void setYearsExperienceMax(Integer years) { this.yearsExperienceMax = years; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Boolean getAutoApplyEnabled() { return autoApplyEnabled; }
    public void setAutoApplyEnabled(Boolean enabled) { this.autoApplyEnabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
