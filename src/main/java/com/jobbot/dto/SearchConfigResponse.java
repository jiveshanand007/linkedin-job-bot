package com.jobbot.dto;

import java.time.LocalDateTime;

public class SearchConfigResponse {
    private Long id;
    private Long userConfigId;
    private Boolean remoteOnly;
    private String experienceLevel;
    private String datePostedFilter;
    private Integer maxPages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserConfigId() { return userConfigId; }
    public void setUserConfigId(Long userConfigId) { this.userConfigId = userConfigId; }
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
