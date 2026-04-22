package com.jobbot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "jobs", uniqueConstraints = @UniqueConstraint(columnNames = {"user_config_id", "linkedin_job_id"}))
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_config_id", nullable = false)
    private UserConfig userConfig;

    @Column(nullable = false)
    private String linkedInJobId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String company;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    @Column
    private Integer salary;

    @Column
    private String applicationType;

    @Column
    private String url;

    @Column
    private String location;

    @Column
    private LocalDateTime extractedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UserConfig getUserConfig() { return userConfig; }
    public void setUserConfig(UserConfig config) { this.userConfig = config; }
    public String getLinkedInJobId() { return linkedInJobId; }
    public void setLinkedInJobId(String jobId) { this.linkedInJobId = jobId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jd) { this.jobDescription = jd; }
    public Integer getSalary() { return salary; }
    public void setSalary(Integer salary) { this.salary = salary; }
    public String getApplicationType() { return applicationType; }
    public void setApplicationType(String type) { this.applicationType = type; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public LocalDateTime getExtractedAt() { return extractedAt; }
    public void setExtractedAt(LocalDateTime at) { this.extractedAt = at; }
}
