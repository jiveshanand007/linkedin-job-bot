package com.jobbot.dto;

public class SearchConfigRequest {
    private Long userConfigId;
    private Boolean remoteOnly;
    private String experienceLevel;
    private String datePostedFilter;
    private Integer maxPages;

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
}
