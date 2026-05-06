# Phase 3a — LinkedIn Job Fetcher Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `LinkedInJobFetcher` stub with a real Playwright-based implementation that logs into LinkedIn, searches for jobs using per-user config, extracts job listings, and returns unsaved `List<Job>` to `SchedulerService`.

**Architecture:** `PlaywrightSessionManager` (plain Java class, not a Spring bean) owns browser lifecycle and login per run. `LinkedInJobFetcher` orchestrates the scrape loop, delegating DOM-to-string extraction to private helpers and string-to-Job mapping to the stateless `JobParser` component. `JobCardData` is the boundary record — no Playwright types cross into `JobParser`.

**Tech Stack:** Java 17, Spring Boot 3.2, Playwright 1.40.0 (already in pom.xml), SQLite/JPA, SLF4J logging.

**No tests** — per project decision (Phase 5). Manual curl commands given for verification.

---

## Chunk 1: Foundation — Exceptions, Data Records, Entities, Repositories

### Task 1: `LoginFailedException`

**Files:**
- Create: `src/main/java/com/jobbot/exception/LoginFailedException.java`

- [ ] **Step 1: Create the exception class**

```java
package com.jobbot.exception;

public class LoginFailedException extends RuntimeException {
    public LoginFailedException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/jobbot/exception/LoginFailedException.java
git commit -m "feat: add LoginFailedException for LinkedIn auth failures"
```

---

### Task 2: `JobCardData` record

**Files:**
- Create: `src/main/java/com/jobbot/entity/JobCardData.java`

This is a plain Java record (no Playwright imports) that acts as the boundary between the Playwright scrape loop and the stateless `JobParser`. All fields are raw strings extracted from DOM — may be blank.

- [ ] **Step 1: Create the record**

```java
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
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/jobbot/entity/JobCardData.java
git commit -m "feat: add JobCardData record — Playwright/JobParser boundary"
```

---

### Task 3: `SearchConfig` entity

**Files:**
- Create: `src/main/java/com/jobbot/entity/SearchConfig.java`

Per-user extra search filters. Keywords and location come from `UserConfig` — no duplication here. `datePostedFilter` defaults to `"ANY"`; null is treated the same as `"ANY"` by the fetcher.

- [ ] **Step 1: Create the entity**

```java
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
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/jobbot/entity/SearchConfig.java
git commit -m "feat: add SearchConfig JPA entity"
```

---

### Task 4: `SearchConfigRepository`

**Files:**
- Create: `src/main/java/com/jobbot/repository/SearchConfigRepository.java`

- [ ] **Step 1: Create the repository**

```java
package com.jobbot.repository;

import com.jobbot.entity.SearchConfig;
import com.jobbot.entity.UserConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SearchConfigRepository extends JpaRepository<SearchConfig, Long> {
    Optional<SearchConfig> findByUserConfig(UserConfig userConfig);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/jobbot/repository/SearchConfigRepository.java
git commit -m "feat: add SearchConfigRepository"
```

---

### Task 5: Add dedup query to `JobRepository`

**Files:**
- Modify: `src/main/java/com/jobbot/repository/JobRepository.java`

Add a `@Query` that returns the set of `linkedInJobId` values already stored for a given user. Used by `LinkedInJobFetcher` for cross-run deduplication.

- [ ] **Step 1: Add the query method**

Current file has:
```java
public interface JobRepository extends JpaRepository<Job, Long> {
    Optional<Job> findByUserConfigAndLinkedInJobId(UserConfig config, String linkedInJobId);
    List<Job> findByUserConfig(UserConfig config);
}
```

Add imports and new method:
```java
package com.jobbot.repository;

import com.jobbot.entity.Job;
import com.jobbot.entity.UserConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface JobRepository extends JpaRepository<Job, Long> {
    Optional<Job> findByUserConfigAndLinkedInJobId(UserConfig config, String linkedInJobId);
    List<Job> findByUserConfig(UserConfig config);

    @Query("SELECT j.linkedInJobId FROM Job j WHERE j.userConfig = :userConfig")
    Set<String> findLinkedInJobIdsByUserConfig(@Param("userConfig") UserConfig userConfig);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/jobbot/repository/JobRepository.java
git commit -m "feat: add findLinkedInJobIdsByUserConfig query to JobRepository"
```

---

### Task 6: `JobParser` — salary extraction and card mapping

**Files:**
- Create: `src/main/java/com/jobbot/service/JobParser.java`

Stateless `@Component`. Zero Playwright imports. `parseCard()` **never throws** — returns `Optional.empty()` for semantically invalid cards. `extractSalary()` normalises all salaries to LPA.

- [ ] **Step 1: Create `JobParser`**

```java
package com.jobbot.service;

import com.jobbot.entity.Job;
import com.jobbot.entity.JobCardData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JobParser {

    private static final Logger logger = LoggerFactory.getLogger(JobParser.class);

    // USD annual salary → approximate LPA  (≈ $1 = ₹83, 1 LPA = ₹1,00,000)
    private static final double USD_ANNUAL_TO_LPA = 0.083;

    // Salary regex patterns (tried in order)
    private static final Pattern INR_RANGE  = Pattern.compile("([\\d.]+)\\s*[\\u2013\\-to]+\\s*([\\d.]+)\\s*LPA", Pattern.CASE_INSENSITIVE);
    private static final Pattern INR_SINGLE = Pattern.compile("([\\d.]+)\\s*LPA", Pattern.CASE_INSENSITIVE);
    private static final Pattern USD_RANGE  = Pattern.compile("\\$([\\d,]+)[Kk]?\\s*[\\u2013\\-]\\s*\\$([\\d,]+)[Kk]?");
    private static final Pattern USD_SINGLE = Pattern.compile("\\$([\\d,]+)[Kk]?");

    /**
     * Maps a JobCardData (plain strings from the DOM) to a Job entity.
     * Never throws. Returns Optional.empty() if linkedInJobId or title are blank.
     */
    public Optional<Job> parseCard(JobCardData data) {
        try {
            if (isBlank(data.linkedInJobId()) || isBlank(data.title())) {
                return Optional.empty();
            }

            Job job = new Job();
            job.setLinkedInJobId(data.linkedInJobId().trim());
            job.setTitle(data.title().trim());
            job.setCompany(isBlank(data.company()) ? "Unknown" : data.company().trim());
            job.setLocation(isBlank(data.location()) ? "" : data.location().trim());
            job.setUrl(isBlank(data.url()) ? "" : data.url().trim());
            job.setJobDescription(isBlank(data.jobDescription()) ? "" : data.jobDescription());
            job.setApplicationType(
                data.applyMethod() != null && data.applyMethod().contains("Easy Apply")
                    ? "EASY_APPLY" : "EXTERNAL"
            );
            job.setSalary(extractSalary(data.jobDescription()));

            return Optional.of(job);
        } catch (Exception e) {
            logger.warn("JobParser.parseCard() unexpected error (should never throw): {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts salary from free text and normalises to LPA (integer).
     * Returns null if no salary pattern found.
     */
    public Integer extractSalary(String text) {
        if (isBlank(text)) return null;

        // 1. INR range: e.g. "₹12–18 LPA" or "12-18 LPA"
        Matcher m = INR_RANGE.matcher(text);
        if (m.find()) {
            double low  = Double.parseDouble(m.group(1));
            double high = Double.parseDouble(m.group(2));
            return (int) Math.round((low + high) / 2.0);
        }

        // 2. INR single: e.g. "15 LPA"
        m = INR_SINGLE.matcher(text);
        if (m.find()) {
            return (int) Math.round(Double.parseDouble(m.group(1)));
        }

        // 3. USD range: e.g. "$80K–$120K"
        m = USD_RANGE.matcher(text);
        if (m.find()) {
            double low  = parseUsdValue(m.group(1));
            double high = parseUsdValue(m.group(2));
            double avgUsd = (low + high) / 2.0;
            return (int) Math.round(avgUsd * USD_ANNUAL_TO_LPA);
        }

        // 4. USD single: e.g. "$90K"
        m = USD_SINGLE.matcher(text);
        if (m.find()) {
            double usd = parseUsdValue(m.group(1));
            return (int) Math.round(usd * USD_ANNUAL_TO_LPA);
        }

        return null;
    }

    // Strips commas; multiplies by 1000 if the original text had K/k suffix
    private double parseUsdValue(String raw) {
        String cleaned = raw.replace(",", "");
        double val = Double.parseDouble(cleaned);
        // Check the character immediately after the match group in original — handled via the pattern
        // The USD_RANGE / USD_SINGLE patterns include K optionally AFTER the digits group,
        // so K is NOT in the captured group. Re-inspect original text at match position
        // is not possible here — we rely on the pattern NOT including K in the group.
        // If K was present the pattern consumed it outside the group, so we multiply:
        // Actually the patterns as written put [Kk]? OUTSIDE the capture group, so we check
        // if the raw captured string ends in a digit and the full match contained K.
        // Simpler: patterns capture digit part only; K suffix check is done by seeing if
        // the value is < 1000 (heuristic: USD salary < $1000 means it was in K units).
        if (val < 1000) val *= 1000; // treat as K (e.g. "90" from "$90K" → 90000)
        return val;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/jobbot/service/JobParser.java
git commit -m "feat: add JobParser — salary extraction and card mapping (no Playwright dependency)"
```

---

## Chunk 2: Playwright Services + REST API + Integration

### Task 7: `PlaywrightSessionManager`

**Files:**
- Create: `src/main/java/com/jobbot/service/PlaywrightSessionManager.java`

Plain Java class — **not** a Spring bean (`@Service` / `@Component` intentionally absent). Instantiated fresh per `fetchJobs` call. Holds `Playwright`, `Browser`, and `Page` as instance fields. `closeSession()` is idempotent — safe to call even if `createSession()` threw.

- [ ] **Step 1: Create `PlaywrightSessionManager`**

```java
package com.jobbot.service;

import com.jobbot.exception.LoginFailedException;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaywrightSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(PlaywrightSessionManager.class);

    private Playwright playwright;
    private Browser browser;
    private Page page;

    /**
     * Opens a headless Chromium browser, navigates to LinkedIn login, and authenticates.
     * Uses UserConfig.linkedInEmail and UserConfig.linkedInPasswordEncrypted (stored as plain text).
     *
     * @throws LoginFailedException if login fails (CAPTCHA, 2FA, timeout, or challenge page detected)
     */
    public Page createSession(String linkedInEmail, String password) {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();

        try {
            page.navigate("https://www.linkedin.com/login");
            page.fill("#username", linkedInEmail);
            page.fill("#password", password);
            page.click("[type=submit]");

            page.waitForURL("**/feed/**", new Page.WaitForURLOptions().setTimeout(10_000));

            String currentUrl = page.url();
            if (currentUrl.contains("checkpoint") || currentUrl.contains("challenge")) {
                throw new LoginFailedException("LinkedIn challenge page detected: " + currentUrl);
            }

            logger.info("LinkedIn login successful for: {}", linkedInEmail);
            return page;

        } catch (PlaywrightException e) {
            throw new LoginFailedException("LinkedIn login timed out or failed: " + e.getMessage());
        }
    }

    /**
     * Closes Page, Browser, and Playwright instance. Idempotent — safe to call even if
     * createSession() never completed.
     */
    public void closeSession() {
        try { if (page != null) page.close(); } catch (Exception ignored) {}
        try { if (browser != null) browser.close(); } catch (Exception ignored) {}
        try { if (playwright != null) playwright.close(); } catch (Exception ignored) {}
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/jobbot/service/PlaywrightSessionManager.java
git commit -m "feat: add PlaywrightSessionManager — headless LinkedIn login, per-run lifecycle"
```

---

### Task 8: `LinkedInJobFetcher` — full implementation

**Files:**
- Modify: `src/main/java/com/jobbot/service/LinkedInJobFetcher.java`

Replaces the stub. Orchestrates: session → search URL → page loop → card extraction → JobParser → dedup → return unsaved list. Does **not** call `jobRepository.save()` — that is `SchedulerService`'s responsibility.

- [ ] **Step 1: Replace stub with full implementation**

```java
package com.jobbot.service;

import com.jobbot.entity.Job;
import com.jobbot.entity.JobCardData;
import com.jobbot.entity.SearchConfig;
import com.jobbot.entity.UserConfig;
import com.jobbot.exception.LoginFailedException;
import com.jobbot.repository.JobRepository;
import com.jobbot.repository.SearchConfigRepository;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LinkedInJobFetcher {

    private static final Logger logger = LoggerFactory.getLogger(LinkedInJobFetcher.class);
    private static final String SEARCH_BASE = "https://www.linkedin.com/jobs/search/";

    @Autowired private SearchConfigRepository searchConfigRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobParser jobParser;

    public List<Job> fetchJobs(UserConfig userConfig) {
        // Resolve search config; use defaults if absent
        Optional<SearchConfig> configOpt = searchConfigRepository.findByUserConfig(userConfig);
        boolean remoteOnly      = configOpt.map(c -> Boolean.TRUE.equals(c.getRemoteOnly())).orElse(false);
        String experienceLevel  = configOpt.map(SearchConfig::getExperienceLevel).orElse(null);
        String datePostedFilter = configOpt.map(c -> c.getDatePostedFilter() != null ? c.getDatePostedFilter() : "ANY").orElse("ANY");
        int maxPages            = configOpt.map(c -> c.getMaxPages() != null ? c.getMaxPages() : 3).orElse(3);

        String password = userConfig.getLinkedInPasswordEncrypted(); // used as-is; no encryption util

        List<Job> collected = new ArrayList<>();
        PlaywrightSessionManager session = null;

        try {
            session = new PlaywrightSessionManager();
            Page page = session.createSession(userConfig.getLinkedInEmail(), password);

            for (int pageIndex = 0; pageIndex < maxPages; pageIndex++) {
                String searchUrl = buildSearchUrl(userConfig, remoteOnly, experienceLevel, datePostedFilter, pageIndex);
                page.navigate(searchUrl);

                try {
                    page.waitForSelector(".job-card-container", new Page.WaitForSelectorOptions().setTimeout(8_000));
                } catch (PlaywrightException e) {
                    logger.warn("Job card container not found on page {} — stopping pagination", pageIndex);
                    break;
                }

                List<Locator> cards = page.locator(".job-card-container").all();
                logger.info("Page {}: found {} cards", pageIndex, cards.size());

                for (Locator card : cards) {
                    try {
                        // Required fields — exception here skips this card
                        String jobId = card.getAttribute("data-job-id");
                        String title = card.locator(".job-card-list__title").innerText();

                        if (jobId == null || jobId.isBlank() || title == null || title.isBlank()) {
                            continue;
                        }

                        // Optional fields — safe helpers return "" on any Playwright exception
                        String company   = safeInnerText(card, ".job-card-container__company-name");
                        String loc       = safeInnerText(card, ".job-card-container__metadata-item");
                        String url       = safeGetAttr(card, "a.job-card-list__title", "href");
                        String applyText = safeInnerText(card, ".job-card-container__apply-method");

                        // Click card to load detail panel, then extract description
                        card.click();
                        String description = "";
                        try {
                            page.waitForSelector(".job-description__container",
                                new Page.WaitForSelectorOptions().setTimeout(5_000));
                            description = page.locator(".job-description__container").innerText();
                        } catch (PlaywrightException te) {
                            logger.warn("Description panel timed out for jobId={}", jobId);
                        }

                        JobCardData data = new JobCardData(jobId, title, company, loc, url, applyText, description);
                        Optional<Job> parsed = jobParser.parseCard(data);

                        if (parsed.isPresent()) {
                            Job job = parsed.get();
                            job.setUserConfig(userConfig);
                            job.setExtractedAt(LocalDateTime.now());
                            collected.add(job);
                        }

                    } catch (Exception e) {
                        // Only Playwright structural failures reach here (required-field extraction)
                        logger.warn("Skipping card due to Playwright error: {}", e.getMessage());
                    }
                }
            }

        } catch (LoginFailedException e) {
            logger.error("LinkedIn login failed for user {}: {}", userConfig.getId(), e.getMessage());
            return List.of(); // finally still runs
        } finally {
            if (session != null) session.closeSession();
        }

        // Within-batch dedup: preserve first occurrence by linkedInJobId
        Map<String, Job> seen = new LinkedHashMap<>();
        collected.forEach(j -> seen.putIfAbsent(j.getLinkedInJobId(), j));
        List<Job> dedupedBatch = new ArrayList<>(seen.values());

        // Cross-run dedup: remove jobs already in DB for this user
        Set<String> existing = jobRepository.findLinkedInJobIdsByUserConfig(userConfig);
        List<Job> newJobs = dedupedBatch.stream()
            .filter(j -> !existing.contains(j.getLinkedInJobId()))
            .toList();

        logger.info("fetchJobs complete for user {}: {} new jobs (from {} collected)",
            userConfig.getId(), newJobs.size(), collected.size());

        return newJobs;
    }

    private String buildSearchUrl(UserConfig config, boolean remoteOnly,
                                   String experienceLevel, String datePostedFilter, int pageIndex) {
        StringBuilder url = new StringBuilder(SEARCH_BASE);
        url.append("?keywords=").append(encode(config.getJobKeywords()));
        url.append("&location=").append(encode(config.getLocation()));

        if (remoteOnly) url.append("&f_WT=2");

        if (experienceLevel != null) {
            int fE = mapExperienceLevel(experienceLevel);
            if (fE > 0) url.append("&f_E=").append(fE);
        }

        if (!"ANY".equalsIgnoreCase(datePostedFilter) && datePostedFilter != null) {
            url.append("&f_TPR=").append(mapDatePostedFilter(datePostedFilter));
        }

        url.append("&start=").append(pageIndex * 25);
        return url.toString();
    }

    private int mapExperienceLevel(String level) {
        return switch (level.toUpperCase()) {
            case "ENTRY"    -> 2;
            case "MID"      -> 4;
            case "SENIOR"   -> 4; // intentional: shares LinkedIn's "Mid-Senior" value
            case "DIRECTOR" -> 5;
            default -> 0; // unknown → omit param
        };
    }

    private String mapDatePostedFilter(String filter) {
        return switch (filter.toUpperCase()) {
            case "PAST_DAY"   -> "r86400";
            case "PAST_WEEK"  -> "r604800";
            case "PAST_MONTH" -> "r2592000";
            default -> "";
        };
    }

    private String encode(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String safeInnerText(Locator parent, String selector) {
        try {
            return parent.locator(selector).first().innerText();
        } catch (Exception e) {
            return "";
        }
    }

    private String safeGetAttr(Locator parent, String selector, String attr) {
        try {
            String val = parent.locator(selector).first().getAttribute(attr);
            return val != null ? val : "";
        } catch (Exception e) {
            return "";
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/jobbot/service/LinkedInJobFetcher.java
git commit -m "feat: implement LinkedInJobFetcher — Playwright login, search, card scraping, dedup"
```

---

### Task 9: Update `SchedulerService` call site

**Files:**
- Modify: `src/main/java/com/jobbot/service/SchedulerService.java`

One-line change: replace old multi-argument stub call with the new single-argument `fetchJobs(config)`.

- [ ] **Step 1: Replace the call site**

In `SchedulerService.executeRun()`, find:
```java
List<Job> fetchedJobs = jobFetcher.searchJobs(
    config, config.getJobKeywords(), config.getYearsExperienceMax(), config.getLocation()
);
```

Replace with:
```java
List<Job> fetchedJobs = jobFetcher.fetchJobs(config);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/jobbot/service/SchedulerService.java
git commit -m "feat: update SchedulerService to call fetchJobs(config)"
```

---

### Task 10: SearchConfig DTOs

**Files:**
- Create: `src/main/java/com/jobbot/dto/SearchConfigRequest.java`
- Create: `src/main/java/com/jobbot/dto/SearchConfigResponse.java`

DTOs prevent exposing the nested `UserConfig` (which contains `linkedInPasswordEncrypted`) in API responses.

- [ ] **Step 1: Create `SearchConfigRequest`**

```java
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
```

- [ ] **Step 2: Create `SearchConfigResponse`**

```java
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
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/jobbot/dto/
git commit -m "feat: add SearchConfigRequest and SearchConfigResponse DTOs"
```

---

### Task 11: `SearchConfigController`

**Files:**
- Create: `src/main/java/com/jobbot/controller/SearchConfigController.java`

REST CRUD for `SearchConfig`. POST creates; PUT does a full replace (null = default). Controller resolves `UserConfig` by `userConfigId` before saving. Maps entities to `SearchConfigResponse` to avoid password exposure.

- [ ] **Step 1: Create `SearchConfigController`**

```java
package com.jobbot.controller;

import com.jobbot.dto.SearchConfigRequest;
import com.jobbot.dto.SearchConfigResponse;
import com.jobbot.entity.SearchConfig;
import com.jobbot.entity.UserConfig;
import com.jobbot.repository.SearchConfigRepository;
import com.jobbot.repository.UserConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/search-config")
@CrossOrigin(origins = "*")
public class SearchConfigController {

    private static final Logger logger = LoggerFactory.getLogger(SearchConfigController.class);

    @Autowired private SearchConfigRepository searchConfigRepository;
    @Autowired private UserConfigRepository userConfigRepository;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SearchConfigRequest req) {
        try {
            if (req.getUserConfigId() == null) {
                return ResponseEntity.badRequest().body("userConfigId is required");
            }

            UserConfig userConfig = userConfigRepository.findById(req.getUserConfigId())
                .orElse(null);
            if (userConfig == null) {
                return ResponseEntity.notFound().build();
            }

            if (searchConfigRepository.findByUserConfig(userConfig).isPresent()) {
                return ResponseEntity.status(409).body("SearchConfig already exists for this user");
            }

            SearchConfig config = new SearchConfig();
            config.setUserConfig(userConfig);
            applyRequest(config, req);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());

            SearchConfig saved = searchConfigRepository.save(config);
            logger.info("SearchConfig created for user {}", req.getUserConfigId());
            return ResponseEntity.status(201).body(toResponse(saved));

        } catch (Exception e) {
            logger.error("Error creating SearchConfig", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SearchConfigRequest req) {
        try {
            SearchConfig config = searchConfigRepository.findById(id).orElse(null);
            if (config == null) return ResponseEntity.notFound().build();

            // Full replace: null fields reset to defaults
            applyRequest(config, req);
            config.setUpdatedAt(LocalDateTime.now());

            SearchConfig saved = searchConfigRepository.save(config);
            return ResponseEntity.ok(toResponse(saved));

        } catch (Exception e) {
            logger.error("Error updating SearchConfig {}", id, e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/user/{userConfigId}")
    public ResponseEntity<?> getByUser(@PathVariable Long userConfigId) {
        try {
            UserConfig userConfig = userConfigRepository.findById(userConfigId).orElse(null);
            if (userConfig == null) return ResponseEntity.notFound().build();

            return searchConfigRepository.findByUserConfig(userConfig)
                .map(c -> ResponseEntity.ok((Object) toResponse(c)))
                .orElse(ResponseEntity.notFound().build());

        } catch (Exception e) {
            logger.error("Error fetching SearchConfig for user {}", userConfigId, e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            if (!searchConfigRepository.existsById(id)) return ResponseEntity.notFound().build();
            searchConfigRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting SearchConfig {}", id, e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // Applies request fields to entity; null = reset to default
    private void applyRequest(SearchConfig config, SearchConfigRequest req) {
        config.setRemoteOnly(req.getRemoteOnly() != null ? req.getRemoteOnly() : false);
        config.setExperienceLevel(req.getExperienceLevel()); // null = any
        config.setDatePostedFilter(req.getDatePostedFilter() != null ? req.getDatePostedFilter() : "ANY");
        config.setMaxPages(req.getMaxPages() != null ? req.getMaxPages() : 3);
    }

    private SearchConfigResponse toResponse(SearchConfig config) {
        SearchConfigResponse resp = new SearchConfigResponse();
        resp.setId(config.getId());
        resp.setUserConfigId(config.getUserConfig().getId());
        resp.setRemoteOnly(config.getRemoteOnly());
        resp.setExperienceLevel(config.getExperienceLevel());
        resp.setDatePostedFilter(config.getDatePostedFilter());
        resp.setMaxPages(config.getMaxPages());
        resp.setCreatedAt(config.getCreatedAt());
        resp.setUpdatedAt(config.getUpdatedAt());
        return resp;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/jobbot/controller/SearchConfigController.java
git commit -m "feat: add SearchConfigController — POST/PUT/GET/DELETE for search config"
```

---

### Task 12: README update + final docs commit

**Files:**
- Modify: `README.md`
- Modify: `COMPONENTS.md`
- Modify: `API_ENDPOINTS.md`
- Modify: `DATABASE_SCHEMA.md`

- [ ] **Step 1: Add Playwright browser install to README**

In the "Setup" / "Getting Started" section of `README.md`, add:

```markdown
### Install Playwright browser binaries (one-time setup)
```bash
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```
```

- [ ] **Step 2: Update COMPONENTS.md**

Update `LinkedInJobFetcher` row from "Phase 3 (Planned)" to "Phase 3a Complete". Add rows for `PlaywrightSessionManager`, `JobParser`, `JobCardData`, `SearchConfig`, `SearchConfigController`.

- [ ] **Step 3: Update API_ENDPOINTS.md**

Add the four `SearchConfig` endpoints (`POST /api/search-config`, `PUT /api/search-config/{id}`, `GET /api/search-config/user/{userConfigId}`, `DELETE /api/search-config/{id}`) with request/response examples.

- [ ] **Step 4: Update DATABASE_SCHEMA.md**

Add `search_config` table schema:
```sql
CREATE TABLE search_config (
  id INTEGER PRIMARY KEY,
  user_config_id INTEGER UNIQUE NOT NULL,
  remote_only BOOLEAN DEFAULT FALSE,
  experience_level TEXT,
  date_posted_filter TEXT DEFAULT 'ANY',
  max_pages INTEGER DEFAULT 3,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
```

- [ ] **Step 5: Commit docs**

```bash
git add README.md COMPONENTS.md API_ENDPOINTS.md DATABASE_SCHEMA.md
git commit -m "docs: update docs for Phase 3a — LinkedInJobFetcher, SearchConfig, Playwright setup"
```

---

### Task 13: Manual verification

No automated tests. Verify with curl after starting the application.

- [ ] **Step 1: Start the application**

```bash
mvn spring-boot:run -q
```
Expected: Application starts on port 8080.

- [ ] **Step 2: Create a UserConfig (if not already present)**

```bash
curl -s -X POST http://localhost:8080/api/config/setup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "linkedInEmail": "your-linkedin@email.com",
    "linkedInPasswordEncrypted": "your-linkedin-password",
    "jobKeywords": "Java developer",
    "location": "Bengaluru",
    "minSalaryLPA": 10,
    "yearsExperienceMax": 5,
    "autoApplyEnabled": false
  }'
```
Expected: 200 with saved UserConfig JSON including `"id": 1`.

- [ ] **Step 3: Create a SearchConfig**

```bash
curl -s -X POST http://localhost:8080/api/search-config \
  -H "Content-Type: application/json" \
  -d '{"userConfigId": 1, "remoteOnly": false, "experienceLevel": "MID", "datePostedFilter": "PAST_WEEK", "maxPages": 1}'
```
Expected: 201 with SearchConfigResponse JSON (no UserConfig nested, no password).

- [ ] **Step 4: Verify GET**

```bash
curl -s http://localhost:8080/api/search-config/user/1
```
Expected: 200 with the SearchConfigResponse.

- [ ] **Step 5: Verify PUT full replace**

```bash
curl -s -X PUT http://localhost:8080/api/search-config/1 \
  -H "Content-Type: application/json" \
  -d '{"remoteOnly": true, "datePostedFilter": "PAST_DAY", "maxPages": 2}'
```
Expected: 200 with updated values; `experienceLevel` reset to null (any).

- [ ] **Step 6: Verify DELETE**

```bash
curl -s -X DELETE http://localhost:8080/api/search-config/1
```
Expected: 204 No Content.

- [ ] **Step 7: Final commit**

```bash
git add -A
git commit -m "feat: Phase 3a complete — LinkedIn job fetcher with Playwright, SearchConfig CRUD

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```
