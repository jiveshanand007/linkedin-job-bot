# Phase 2 Resume Tailoring Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate Claude API resume tailoring into the LinkedIn job bot so every matched job gets a LaTeX resume tailored to its description, stored in the database.

**Architecture:** `ClaudeApiClient` makes raw HTTP calls to Anthropic's messages API. `ResumeTailor` orchestrates the call and persists the tailored `Resume` entity. `SchedulerService` calls `ResumeTailor` for each matched job after saving them. A `ResumeController` exposes an on-demand tailor endpoint aligned with the existing API docs.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring RestTemplate, Jackson ObjectMapper

**Spec:** `docs/superpowers/specs/2026-05-06-phase2-resume-tailoring-design.md`

---

## Chunk 1: Entity, Repository, Config

---

### Task 1: Add `parentResumeId` and `jobId` to `Resume` entity

**Files:**
- Modify: `src/main/java/com/jobbot/entity/Resume.java`

- [ ] **Step 1: Add two new fields to `Resume.java`**

Add after the existing `private LocalDateTime updatedAt;` field:

```java
@Column
private Long parentResumeId;

@Column
private Long jobId;
```

Add getters/setters at the end of the class (before the closing `}`):

```java
public Long getParentResumeId() { return parentResumeId; }
public void setParentResumeId(Long id) { this.parentResumeId = id; }
public Long getJobId() { return jobId; }
public void setJobId(Long id) { this.jobId = id; }
```

- [ ] **Step 2: Update `DATABASE_SCHEMA.md` to document new Resume fields**

In `DATABASE_SCHEMA.md`, find the `resumes` table columns section and add two new rows:

```
| parent_resume_id | BIGINT | YES | FK → resumes.id — null for base resumes, set for tailored versions |
| job_id           | BIGINT | YES | FK → jobs.id — which job this resume was tailored for |
```

Also add a note under the table:
```
**Tailored Resumes:**
- `is_active` = false for all tailored resumes (only base resumes are active)
- `version_name` = "tailored-for-{jobId}" for auto-generated resumes
- `parent_resume_id` links back to the base resume used as source
```

- [ ] **Step 3: Compile to verify**

```bash
cd linkedin-job-bot
mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jobbot/entity/Resume.java DATABASE_SCHEMA.md
git commit -m "feat: add parentResumeId and jobId fields to Resume entity

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 2: Add active resume lookup to `ResumeRepository`

**Files:**
- Modify: `src/main/java/com/jobbot/repository/ResumeRepository.java`

- [ ] **Step 1: Add `findFirstByUserConfigAndIsActive` to `ResumeRepository`**

Full updated file:

```java
package com.jobbot.repository;

import com.jobbot.entity.Resume;
import com.jobbot.entity.UserConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    List<Resume> findByUserConfig(UserConfig config);
    List<Resume> findByUserConfigAndIsActive(UserConfig config, Boolean active);
    Optional<Resume> findFirstByUserConfigAndIsActive(UserConfig config, Boolean active);
}
```

- [ ] **Step 2: Compile to verify**

```bash
mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/jobbot/repository/ResumeRepository.java
git commit -m "feat: add findFirstByUserConfigAndIsActive to ResumeRepository

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 3: Add Claude config to `application.properties` and create `ClaudeApiConfig`

**Files:**
- Modify: `src/main/resources/application.properties`
- Create: `src/main/java/com/jobbot/config/ClaudeApiConfig.java`

- [ ] **Step 1: Add two missing properties to `application.properties`**

The file already has `claude.api.key`. Add below it:

```properties
claude.model=claude-haiku-4-5
claude.api.url=https://api.anthropic.com/v1/messages
```

- [ ] **Step 2: Create `ClaudeApiConfig.java`**

Create `src/main/java/com/jobbot/config/ClaudeApiConfig.java`:

```java
package com.jobbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ClaudeApiConfig {

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.model}")
    private String model;

    @Value("${claude.api.url}")
    private String apiUrl;

    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getApiUrl() { return apiUrl; }

    @Bean
    @org.springframework.context.annotation.Primary
    public RestTemplate claudeRestTemplate() {
        return new RestTemplate();
    }
}
```

- [ ] **Step 3: Compile to verify**

```bash
mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application.properties \
        src/main/java/com/jobbot/config/ClaudeApiConfig.java
git commit -m "feat: add Claude API configuration and RestTemplate bean

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Chunk 2: Claude API Client + ResumeTailor Service

---

### Task 4: Create `ClaudeApiClient`

**Files:**
- Create: `src/main/java/com/jobbot/service/ClaudeApiClient.java`

- [ ] **Step 1: Create `ClaudeApiClient.java`**

Create `src/main/java/com/jobbot/service/ClaudeApiClient.java`:

```java
package com.jobbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbot.config.ClaudeApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;

@Service
public class ClaudeApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeApiClient.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 4096;

    @Autowired
    private RestTemplate claudeRestTemplate;

    @Autowired
    private ClaudeApiConfig claudeApiConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String rewriteResume(String latexContent, String jobTitle, String company, String jobDescription) {
        String prompt = buildPrompt(latexContent, jobTitle, company, jobDescription);
        String requestBody = buildRequestBody(prompt);
        HttpEntity<String> request = buildHttpEntity(requestBody);

        try {
            ResponseEntity<String> response = claudeRestTemplate.postForEntity(
                claudeApiConfig.getApiUrl(), request, String.class
            );
            return extractTextFromResponse(response.getBody());
        } catch (Exception e) {
            logger.error("Claude API call failed: {}", e.getMessage());
            throw new RuntimeException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(String latexContent, String jobTitle, String company, String jd) {
        return String.format("""
            You are a resume editor. Given a LaTeX resume and a job description, \
            rewrite ONLY these sections to better match the job:
            1. Summary / objective section
            2. Skills list (reorder to surface relevant skills first, do not add fake skills)
            3. Experience bullet points (rephrase verbs, emphasize relevant tech)

            Rules:
            - Do NOT change dates, company names, job titles, or education
            - Do NOT add experience or skills the candidate does not have
            - Keep all LaTeX commands and formatting exactly intact
            - Return ONLY the modified LaTeX, no explanation or markdown wrapper

            JOB: %s at %s
            JD: %s

            RESUME:
            %s""", jobTitle, company, jd, latexContent);
    }

    private String buildRequestBody(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                "model", claudeApiConfig.getModel(),
                "max_tokens", MAX_TOKENS,
                "messages", List.of(Map.of("role", "user", "content", prompt))
            );
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build request body", e);
        }
    }

    private HttpEntity<String> buildHttpEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", claudeApiConfig.getApiKey());
        headers.set("anthropic-version", ANTHROPIC_VERSION);
        return new HttpEntity<>(body, headers);
    }

    private String extractTextFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode textNode = root.path("content").get(0).path("text");
            if (textNode.isMissingNode()) {
                throw new RuntimeException("Unexpected Claude response structure");
            }
            return textNode.asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Claude response: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 2: Compile to verify**

```bash
mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/jobbot/service/ClaudeApiClient.java
git commit -m "feat: add ClaudeApiClient for Anthropic messages API

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 5: Create `ResumeTailor` service

**Files:**
- Create: `src/main/java/com/jobbot/service/ResumeTailor.java`

- [ ] **Step 1: Create `ResumeTailor.java`**

Create `src/main/java/com/jobbot/service/ResumeTailor.java`:

```java
package com.jobbot.service;

import com.jobbot.entity.Job;
import com.jobbot.entity.Resume;
import com.jobbot.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ResumeTailor {

    private static final Logger logger = LoggerFactory.getLogger(ResumeTailor.class);

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Autowired
    private ResumeRepository resumeRepository;

    /**
     * Tailors baseResume for the given job, persists the result, and returns it.
     * Returns Optional.empty() on failure — callers use this to detect and count errors.
     */
    public Optional<Resume> tailorAndSave(Job job, Resume baseResume) {
        try {
            String tailoredLatex = claudeApiClient.rewriteResume(
                baseResume.getLatexContent(),
                job.getTitle(),
                job.getCompany(),
                job.getJobDescription()
            );

            Resume tailored = new Resume();
            tailored.setUserConfig(baseResume.getUserConfig());
            tailored.setParentResumeId(baseResume.getId());
            tailored.setJobId(job.getId());
            tailored.setVersionName("tailored-for-" + job.getId());
            tailored.setLatexContent(tailoredLatex);
            tailored.setIsActive(false);
            tailored.setUploadedAt(LocalDateTime.now());
            tailored.setUpdatedAt(LocalDateTime.now());

            Resume saved = resumeRepository.save(tailored);
            logger.info("Tailored resume saved for job {} ({})", job.getId(), job.getTitle());
            return Optional.of(saved);

        } catch (Exception e) {
            logger.error("Failed to tailor resume for job {}: {}", job.getId(), e.getMessage());
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 2: Update `COMPONENTS.md`**

Find the `ResumeTailor (Phase 2 - NOT YET IMPLEMENTED)` section and replace the planned methods with the actual implementation:

```java
// Replace planned:
String generateTailoredResume(Resume baseResume, Job job)
String generateCoverLetter(Resume baseResume, Job job)

// With actual:
Optional<Resume> tailorAndSave(Job job, Resume baseResume)
```

Also change `Phase Status:` from `Phase 2 - NOT YET IMPLEMENTED` → `Phase 2 - COMPLETE ✅`

- [ ] **Step 3: Compile to verify**

```bash
mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jobbot/service/ResumeTailor.java COMPONENTS.md
git commit -m "feat: add ResumeTailor service with Claude API integration

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Chunk 3: Controller + SchedulerService Integration

---

### Task 6: Create `ResumeController` with on-demand tailor endpoint

**Files:**
- Create: `src/main/java/com/jobbot/controller/ResumeController.java`

The existing `API_ENDPOINTS.md` documents the endpoint as `POST /api/resumes/tailor?resumeId=1&jobId=5`.
We use `resumeId` (caller selects which resume) + `jobId` as query params, matching the doc.

- [ ] **Step 1: Create `ResumeController.java`**

Create `src/main/java/com/jobbot/controller/ResumeController.java`:

```java
package com.jobbot.controller;

import com.jobbot.entity.Job;
import com.jobbot.entity.Resume;
import com.jobbot.repository.JobRepository;
import com.jobbot.repository.ResumeRepository;
import com.jobbot.service.ResumeTailor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/resumes")
@CrossOrigin(origins = "*")
public class ResumeController {

    private static final Logger logger = LoggerFactory.getLogger(ResumeController.class);

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeTailor resumeTailor;

    @PostMapping("/tailor")
    public ResponseEntity<?> tailorResume(
            @RequestParam Long resumeId,
            @RequestParam Long jobId) {

        Optional<Resume> baseResumeOpt = resumeRepository.findById(resumeId);
        if (baseResumeOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Resume not found: " + resumeId));
        }

        Optional<Job> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Job not found: " + jobId));
        }

        Optional<Resume> tailored = resumeTailor.tailorAndSave(jobOpt.get(), baseResumeOpt.get());
        if (tailored.isEmpty()) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Tailoring failed — check logs"));
        }

        Resume r = tailored.get();
        return ResponseEntity.ok(Map.of(
            "resumeId", r.getId(),
            "versionName", r.getVersionName(),
            "latexContent", r.getLatexContent()
        ));
    }
}
```

- [ ] **Step 2: Update `API_ENDPOINTS.md`**

Find the Phase 2+ tailor resume placeholder entry:
```
POST /api/resumes/tailor?resumeId=1&jobId=5
  → Returns tailored LaTeX + PDF + cover letter
```

Replace with the full documented endpoint matching the implementation:
```
POST /api/resumes/tailor?resumeId=1&jobId=5
  → Tailors the specified resume for the given job using Claude API
  → Stores result as a new resume version (isActive=false)

Response 200:
{
  "resumeId": 99,
  "versionName": "tailored-for-5",
  "latexContent": "\\documentclass{article}..."
}

Response 400: { "error": "Resume not found: 1" }
Response 500: { "error": "Tailoring failed — check logs" }
```

Also move it from `Phase 2+ APIs (Not Yet Implemented)` section into the main API list.

- [ ] **Step 3: Compile to verify**

```bash
mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jobbot/controller/ResumeController.java \
        API_ENDPOINTS.md
git commit -m "feat: add ResumeController with POST /api/resumes/tailor endpoint

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 7: Integrate `ResumeTailor` into `SchedulerService`

**Files:**
- Modify: `src/main/java/com/jobbot/service/SchedulerService.java`

- [ ] **Step 1: Add `ResumeTailor` and `ResumeRepository` dependencies**

Add to the `@Autowired` fields block in `SchedulerService`:

```java
@Autowired
private ResumeRepository resumeRepository;

@Autowired
private ResumeTailor resumeTailor;
```

Add the missing import at the top:
```java
import com.jobbot.entity.Resume;
import com.jobbot.repository.ResumeRepository;
import com.jobbot.service.ResumeTailor;
import java.util.Optional;
```

- [ ] **Step 2: Add tailoring loop after `jobRepository.saveAll`**

In `executeRun`, after `matchedJobs.forEach(jobRepository::save);`, add:

```java
Optional<Resume> activeResume = resumeRepository.findFirstByUserConfigAndIsActive(config, true);

int tailoringErrors = 0;
if (activeResume.isPresent()) {
    for (Job job : matchedJobs) {
        Optional<Resume> tailored = resumeTailor.tailorAndSave(job, activeResume.get());
        if (tailored.isEmpty()) tailoringErrors++;
    }
} else {
    logger.warn("No active base resume for user {} — skipping tailoring", userId);
}
```

- [ ] **Step 3: Add `tailoringErrors` to the result map**

In the result section, add:
```java
result.put("tailoringErrors", tailoringErrors);
```

The full updated `executeRun` method should look like:

```java
public Map<String, Object> executeRun(Long userId) {
    Map<String, Object> result = new HashMap<>();

    try {
        UserConfig config = userConfigRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User config not found"));

        logger.info("Starting job search for user: {}", userId);

        List<Job> fetchedJobs = jobFetcher.searchJobs(
            config, config.getJobKeywords(), config.getYearsExperienceMax(), config.getLocation()
        );

        List<Job> matchedJobs = jobMatcher.filterJobs(fetchedJobs, config);
        matchedJobs.forEach(jobRepository::save);

        Optional<Resume> activeResume = resumeRepository.findFirstByUserConfigAndIsActive(config, true);

        int tailoringErrors = 0;
        if (activeResume.isPresent()) {
            for (Job job : matchedJobs) {
                Optional<Resume> tailored = resumeTailor.tailorAndSave(job, activeResume.get());
                if (tailored.isEmpty()) tailoringErrors++;
            }
        } else {
            logger.warn("No active base resume for user {} — skipping tailoring", userId);
        }

        result.put("status", "success");
        result.put("jobsFetched", fetchedJobs.size());
        result.put("jobsMatched", matchedJobs.size());
        result.put("applicationsSubmitted", 0);
        result.put("tailoringErrors", tailoringErrors);

        logger.info("Run completed for user {}. Matched {} jobs, {} tailoring errors",
            userId, matchedJobs.size(), tailoringErrors);

    } catch (Exception e) {
        logger.error("Error executing bot run", e);
        result.put("status", "failed");
        result.put("error", e.getMessage());
    }

    return result;
}
```

- [ ] **Step 4: Update `COMPONENTS.md` and `CONTEXT_FOR_FUTURE_SESSIONS.md`**

In `COMPONENTS.md`, find the `SchedulerService` Dependencies section and change:
```
- ResumeTailor (Phase 3)
```
to:
```
- ResumeTailor (Phase 2) ✅
```

In `CONTEXT_FOR_FUTURE_SESSIONS.md`, update the Phase status:
```
| Phase Status | Phase 1 ✅ / Phase 2 ✅ / Phase 3-5 ⏳ |
```

And move `ResumeTailor` from the "What's NOT Implemented" section to "What's Implemented".

- [ ] **Step 5: Compile to verify**

```bash
mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jobbot/service/SchedulerService.java \
        COMPONENTS.md CONTEXT_FOR_FUTURE_SESSIONS.md
git commit -m "feat: integrate ResumeTailor into SchedulerService pipeline

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 8: Final verification and docs commit

- [ ] **Step 1: Full project compile**

```bash
mvn clean compile 2>&1 | grep -E "(BUILD|ERROR)" | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: Verify all new files exist**

```bash
find src/main -name "*.java" | sort
```

Expected output includes these new files:
```
src/main/java/com/jobbot/config/ClaudeApiConfig.java
src/main/java/com/jobbot/controller/ResumeController.java
src/main/java/com/jobbot/service/ClaudeApiClient.java
src/main/java/com/jobbot/service/ResumeTailor.java
```

- [ ] **Step 3: Commit updated plan and spec docs**

```bash
git add docs/superpowers/specs/2026-05-06-phase2-resume-tailoring-design.md \
        docs/superpowers/plans/2026-05-06-phase2-resume-tailoring.md
git commit -m "docs: update Phase 2 plan (no tests, aligned API endpoints)

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```
