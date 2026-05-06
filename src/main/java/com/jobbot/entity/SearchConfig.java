package com.jobbot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "search_config")
public class SearchConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_config_id", nullable = false, unique = true)
    private UserConfig userConfig;

    @Column
    private Boolean remoteOnly = false;

    @Column
    private String experienceLevel; // ENTRY | MID | SENIOR | DIRECTOR | null = any

    @Column
    private String datePostedFilter = "ANY"; // PAST_DAY | PAST_WEEK | PAST_MONTH | ANY; null = ANY

    @Column
    private Integer maxPages = 3; // [1..10]; 3 → up to 75 results per run

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UserConfig getUserConfig() { return userConfig; }
    public void setUserConfig(UserConfig userConfig) { this.userConfig = userConfig; }
    public Boolean getRemoteOnly() { return remoteOnly; }
    public void setRemoteOnly(Boolean remoteOnly) { this.remoteOnly = remoteOnly; }
    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }
    public String getDatePostedFilter() { return datePostedFilter; }
    public void setDatePostedFilter(String datePostedFilter) { this.datePostedFilter = datePostedFilter; }
    public Integer getMaxPages() { return maxPages; }
    public void setMaxPages(Integer maxPages) { this.maxPages = maxPages; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
