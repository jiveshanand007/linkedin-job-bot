package com.jobbot.entity;

public record JobCardData(
    String linkedInJobId,   // from data-job-id attribute; blank → card skipped by JobParser
    String title,           // from .job-card-list__title; blank → card skipped by JobParser
    String company,         // from .job-card-container__company-name; blank → "Unknown"
    String location,        // from .job-card-container__metadata-item; blank → ""
    String url,             // from a.job-card-list__title href; blank → ""
    String applyMethod,     // raw DOM text e.g. "Easy Apply" or ""; blank → "EXTERNAL"
    String jobDescription   // full text from detail panel; blank → ""
) {}
