# Phase 2 Resume Tailoring Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate Claude API resume tailoring into the LinkedIn job bot so every matched job gets a LaTeX resume tailored to its description, stored in the database.

**Architecture:** `ClaudeApiClient` makes raw HTTP calls to Anthropic's messages API. `ResumeTailor` orchestrates the call and persists the tailored `Resume` entity. `SchedulerService` calls `ResumeTailor` for each matched job after saving them. A `ResumeController` exposes an on-demand tailor endpoint.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring RestTemplate, Jackson ObjectMapper, JUnit 5, Mockito

**Spec:** `docs/superpowers/specs/2026-05-06-phase2-resume-tailoring-design.md`

---

## Chunk 1: Entity, Repository, Config

---

### Task 1: Add `parentResumeId` and `jobId` to `Resume` entity

**Files:**
- Modify: `src/main/java/com/jobbot/entity/Resume.java`
- Test: `src/test/java/com/jobbot/entity/ResumeEntityTest.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/jobbot/entity/ResumeEntityTest.java`:

```java
package com.jobbot.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResumeEntityTest {

    @Test
    void tailoredResumeFields_canBeSetAndRead() {
        Resume resume = new Resume();
        resume.setParentResumeId(1L);
        resume.setJobId(42L);

        assertEquals(1L, resume.getParentResumeId());
        assertEquals(42L, resume.getJobId());
    }

    @Test
    void newResume_hasNullTailoringFields() {
        Resume resume = new Resume();
        assertNull(resume.getParentResumeId());
        assertNull(resume.getJobId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd linkedin-job-bot
mvn test -Dtest=ResumeEntityTest -q 2>&1 | tail -20
```

Expected: FAIL — `getParentResumeId()` and `getJobId()` methods don't exist.

- [ ] **Step 3: Add fields to `Resume.java`**

Add after the existing `@Column private LocalDateTime updatedAt;` field:

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

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=ResumeEntityTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jobbot/entity/Resume.java \
        src/test/java/com/jobbot/entity/ResumeEntityTest.java
git commit -m "feat: add parentResumeId and jobId fields to Resume entity

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 2: Add active resume lookup to `ResumeRepository`

**Files:**
- Modify: `src/main/java/com/jobbot/repository/ResumeRepository.java`
- Test: `src/test/java/com/jobbot/repository/ResumeRepositoryTest.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/jobbot/repository/ResumeRepositoryTest.java`:

```java
package com.jobbot.repository;

import com.jobbot.entity.Resume;
import com.jobbot.entity.UserConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ResumeRepositoryTest {

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private com.jobbot.repository.UserConfigRepository userConfigRepository;

    @Test
    void findFirstActiveByUserConfig_returnsActiveResume() {
        UserConfig config = new UserConfig();
        config.setEmail("test@test.com");
        config.setLinkedInEmail("test@linkedin.com");
        config.setLinkedInPasswordEncrypted("encrypted-pass");
        config.setJobKeywords("Java");
        config.setMinSalaryLPA(10);
        config.setYearsExperienceMax(3);
        config.setLocation("Bangalore");
        config.setCreatedAt(LocalDateTime.now());
        userConfigRepository.save(config);

        Resume resume = new Resume();
        resume.setUserConfig(config);
        resume.setVersionName("base");
        resume.setLatexContent("\\documentclass{article}");
        resume.setIsActive(true);
        resume.setUploadedAt(LocalDateTime.now());
        resumeRepository.save(resume);

        Optional<Resume> found = resumeRepository.findFirstByUserConfigAndIsActive(config, true);
        assertTrue(found.isPresent());
        assertEquals("base", found.get().getVersionName());
    }

    @Test
    void findFirstActiveByUserConfig_returnsEmpty_whenNoneActive() {
        UserConfig config = new UserConfig();
        config.setEmail("none@test.com");
        config.setLinkedInEmail("none@linkedin.com");
        config.setLinkedInPasswordEncrypted("encrypted-pass");
        config.setJobKeywords("Java");
        config.setMinSalaryLPA(10);
        config.setYearsExperienceMax(3);
        config.setLocation("Bangalore");
        config.setCreatedAt(LocalDateTime.now());
        userConfigRepository.save(config);

        Optional<Resume> found = resumeRepository.findFirstByUserConfigAndIsActive(config, true);
        assertFalse(found.isPresent());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=ResumeRepositoryTest -q 2>&1 | tail -20
```

Expected: FAIL — method `findFirstByUserConfigAndIsActive` doesn't exist.

- [ ] **Step 3: Add method to `ResumeRepository`**

Add to `ResumeRepository.java`:

```java
import java.util.Optional;

// Add to interface body:
Optional<Resume> findFirstByUserConfigAndIsActive(UserConfig config, Boolean active);
```

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

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=ResumeRepositoryTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jobbot/repository/ResumeRepository.java \
        src/test/java/com/jobbot/repository/ResumeRepositoryTest.java
git commit -m "feat: add findFirstByUserConfigAndIsActive to ResumeRepository

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 3: Add Claude config to `application.properties` and create `ClaudeApiConfig`

**Files:**
- Modify: `src/main/resources/application.properties`
- Create: `src/main/java/com/jobbot/config/ClaudeApiConfig.java`
- Test: `src/test/java/com/jobbot/config/ClaudeApiConfigTest.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/jobbot/config/ClaudeApiConfigTest.java`:

```java
package com.jobbot.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ClaudeApiConfigTest {

    @Autowired
    private ClaudeApiConfig claudeApiConfig;

    @Autowired
    private RestTemplate claudeRestTemplate;

    @Test
    void claudeRestTemplate_isProvided() {
        assertNotNull(claudeRestTemplate);
    }

    @Test
    void claudeApiConfig_readsApiKey() {
        assertNotNull(claudeApiConfig.getApiKey());
        assertFalse(claudeApiConfig.getApiKey().isBlank());
    }

    @Test
    void claudeApiConfig_readsModel() {
        assertNotNull(claudeApiConfig.getModel());
        assertFalse(claudeApiConfig.getModel().isBlank());
    }

    @Test
    void claudeApiConfig_readsApiUrl() {
        assertNotNull(claudeApiConfig.getApiUrl());
        assertTrue(claudeApiConfig.getApiUrl().startsWith("https://"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=ClaudeApiConfigTest -q 2>&1 | tail -20
```

Expected: FAIL — `ClaudeApiConfig` class doesn't exist.

- [ ] **Step 3: Add config properties to `application.properties`**

Add these lines to `src/main/resources/application.properties` (the `claude.api.key` line already exists — add the two new ones below it):

```properties
claude.model=claude-haiku-4-5
claude.api.url=https://api.anthropic.com/v1/messages
```

- [ ] **Step 4: Create `ClaudeApiConfig.java`**

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
    public RestTemplate claudeRestTemplate() {
        return new RestTemplate();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn test -Dtest=ClaudeApiConfigTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, 4 tests passed.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/application.properties \
        src/main/java/com/jobbot/config/ClaudeApiConfig.java \
        src/test/java/com/jobbot/config/ClaudeApiConfigTest.java
git commit -m "feat: add Claude API configuration and RestTemplate bean

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Chunk 2: Claude API Client + ResumeTailor Service

---

### Task 4: Create `ClaudeApiClient`

**Files:**
- Create: `src/main/java/com/jobbot/service/ClaudeApiClient.java`
- Test: `src/test/java/com/jobbot/service/ClaudeApiClientTest.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/jobbot/service/ClaudeApiClientTest.java`:

```java
package com.jobbot.service;

import com.jobbot.config.ClaudeApiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaudeApiClientTest {

    @Mock
    private RestTemplate claudeRestTemplate;

    @Mock
    private ClaudeApiConfig claudeApiConfig;

    @InjectMocks
    private ClaudeApiClient claudeApiClient;

    @BeforeEach
    void setup() {
        when(claudeApiConfig.getApiKey()).thenReturn("test-key");
        when(claudeApiConfig.getModel()).thenReturn("claude-haiku-4-5");
        when(claudeApiConfig.getApiUrl()).thenReturn("https://api.anthropic.com/v1/messages");
    }

    @Test
    void rewriteResume_returnsModifiedLatex() {
        String fakeResponse = """
            {
              "content": [{"type": "text", "text": "\\\\documentclass{article} TAILORED"}]
            }
            """;
        ResponseEntity<String> mockResp = new ResponseEntity<>(fakeResponse, HttpStatus.OK);
        when(claudeRestTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(mockResp);

        String result = claudeApiClient.rewriteResume(
            "\\documentclass{article}", "SWE", "Acme Corp", "We need Java developers"
        );

        assertEquals("\\documentclass{article} TAILORED", result);
    }

    @Test
    void rewriteResume_onRestClientException_throwsRuntimeException() {
        when(claudeRestTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RestClientException("Network error"));

        assertThrows(RuntimeException.class, () ->
            claudeApiClient.rewriteResume("latex", "SWE", "Acme", "jd")
        );
    }

    @Test
    void rewriteResume_onMalformedResponse_throwsRuntimeException() {
        ResponseEntity<String> mockResp = new ResponseEntity<>("{\"bad\": \"json\"}", HttpStatus.OK);
        when(claudeRestTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(mockResp);

        assertThrows(RuntimeException.class, () ->
            claudeApiClient.rewriteResume("latex", "SWE", "Acme", "jd")
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=ClaudeApiClientTest -q 2>&1 | tail -20
```

Expected: FAIL — `ClaudeApiClient` class doesn't exist.

- [ ] **Step 3: Create `ClaudeApiClient.java`**

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

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=ClaudeApiClientTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jobbot/service/ClaudeApiClient.java \
        src/test/java/com/jobbot/service/ClaudeApiClientTest.java
git commit -m "feat: add ClaudeApiClient for Anthropic messages API

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 5: Create `ResumeTailor` service

**Files:**
- Create: `src/main/java/com/jobbot/service/ResumeTailor.java`
- Test: `src/test/java/com/jobbot/service/ResumeTailorTest.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/jobbot/service/ResumeTailorTest.java`:

```java
package com.jobbot.service;

import com.jobbot.entity.Job;
import com.jobbot.entity.Resume;
import com.jobbot.entity.UserConfig;
import com.jobbot.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeTailorTest {

    @Mock
    private ClaudeApiClient claudeApiClient;

    @Mock
    private ResumeRepository resumeRepository;

    @InjectMocks
    private ResumeTailor resumeTailor;

    private Job job;
    private Resume baseResume;
    private UserConfig userConfig;

    @BeforeEach
    void setup() {
        userConfig = new UserConfig();
        userConfig.setEmail("user@test.com");

        baseResume = new Resume();
        baseResume.setId(1L);
        baseResume.setLatexContent("\\documentclass{article} BASE");
        baseResume.setVersionName("base-v1");
        baseResume.setUserConfig(userConfig);

        job = new Job();
        job.setId(42L);
        job.setTitle("Software Engineer");
        job.setCompany("Acme Corp");
        job.setJobDescription("We need Java Spring Boot expertise.");
    }

    @Test
    void tailorAndSave_returnsPopulatedResume_onSuccess() {
        String tailoredLatex = "\\documentclass{article} TAILORED";
        when(claudeApiClient.rewriteResume(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(tailoredLatex);

        Resume persisted = new Resume();
        persisted.setId(99L);
        persisted.setLatexContent(tailoredLatex);
        persisted.setVersionName("tailored-for-42");

        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        when(resumeRepository.save(captor.capture())).thenReturn(persisted);

        Optional<Resume> result = resumeTailor.tailorAndSave(job, baseResume);

        assertTrue(result.isPresent());
        assertEquals(99L, result.get().getId());

        Resume saved = captor.getValue();
        assertEquals(tailoredLatex, saved.getLatexContent());
        assertEquals(1L, saved.getParentResumeId());
        assertEquals(42L, saved.getJobId());
        assertEquals("tailored-for-42", saved.getVersionName());
        assertFalse(saved.getIsActive());
        assertEquals(userConfig, saved.getUserConfig());
    }

    @Test
    void tailorAndSave_onClaudeFailure_returnsEmpty() {
        when(claudeApiClient.rewriteResume(anyString(), anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("API down"));

        Optional<Resume> result = resumeTailor.tailorAndSave(job, baseResume);

        assertFalse(result.isPresent());
        verify(resumeRepository, never()).save(any());
    }

    @Test
    void tailorAndSave_passesCorrectFieldsToClient() {
        when(claudeApiClient.rewriteResume(anyString(), anyString(), anyString(), anyString()))
            .thenReturn("tailored");
        when(resumeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        resumeTailor.tailorAndSave(job, baseResume);

        verify(claudeApiClient).rewriteResume(
            "\\documentclass{article} BASE",
            "Software Engineer",
            "Acme Corp",
            "We need Java Spring Boot expertise."
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=ResumeTailorTest -q 2>&1 | tail -20
```

Expected: FAIL — `ResumeTailor` class doesn't exist.

- [ ] **Step 3: Create `ResumeTailor.java`**

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

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=ResumeTailorTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jobbot/service/ResumeTailor.java \
        src/test/java/com/jobbot/service/ResumeTailorTest.java
git commit -m "feat: add ResumeTailor service with Claude API integration

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

## Chunk 3: Controller + SchedulerService Integration

---

### Task 6: Create `ResumeController` with on-demand tailor endpoint

**Files:**
- Create: `src/main/java/com/jobbot/controller/ResumeController.java`
- Test: `src/test/java/com/jobbot/controller/ResumeControllerTest.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/jobbot/controller/ResumeControllerTest.java`:

```java
package com.jobbot.controller;

import com.jobbot.entity.Job;
import com.jobbot.entity.Resume;
import com.jobbot.entity.UserConfig;
import com.jobbot.repository.JobRepository;
import com.jobbot.repository.ResumeRepository;
import com.jobbot.repository.UserConfigRepository;
import com.jobbot.service.ResumeTailor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeControllerTest {

    @Mock
    private UserConfigRepository userConfigRepository;

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ResumeTailor resumeTailor;

    @InjectMocks
    private ResumeController resumeController;

    @Test
    void tailorResume_returns200_withBodyFields_onSuccess() {
        UserConfig config = new UserConfig();
        config.setId(1L);

        Resume base = new Resume();
        base.setId(10L);

        Job job = new Job();
        job.setId(42L);

        Resume tailored = new Resume();
        tailored.setId(99L);
        tailored.setVersionName("tailored-for-42");
        tailored.setLatexContent("TAILORED LATEX");

        when(userConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(resumeRepository.findFirstByUserConfigAndIsActive(config, true)).thenReturn(Optional.of(base));
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job));
        when(resumeTailor.tailorAndSave(job, base)).thenReturn(Optional.of(tailored));

        ResponseEntity<?> response = resumeController.tailorResume(Map.of("userId", 1, "jobId", 42));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(99L, body.get("resumeId"));
        assertEquals("tailored-for-42", body.get("versionName"));
        assertEquals("TAILORED LATEX", body.get("latexContent"));
    }

    @Test
    void tailorResume_returns500_whenTailoringFails() {
        UserConfig config = new UserConfig();
        Resume base = new Resume();
        Job job = new Job();
        job.setId(42L);

        when(userConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(resumeRepository.findFirstByUserConfigAndIsActive(config, true)).thenReturn(Optional.of(base));
        when(jobRepository.findById(42L)).thenReturn(Optional.of(job));
        when(resumeTailor.tailorAndSave(job, base)).thenReturn(Optional.empty());

        ResponseEntity<?> response = resumeController.tailorResume(Map.of("userId", 1, "jobId", 42));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void tailorResume_returns400_whenUserNotFound() {
        when(userConfigRepository.findById(anyLong())).thenReturn(Optional.empty());

        ResponseEntity<?> response = resumeController.tailorResume(Map.of("userId", 99, "jobId", 1));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(resumeTailor, never()).tailorAndSave(any(), any());
    }

    @Test
    void tailorResume_returns400_whenNoActiveResume() {
        UserConfig config = new UserConfig();
        when(userConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(resumeRepository.findFirstByUserConfigAndIsActive(config, true)).thenReturn(Optional.empty());

        ResponseEntity<?> response = resumeController.tailorResume(Map.of("userId", 1, "jobId", 42));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(resumeTailor, never()).tailorAndSave(any(), any());
    }

    @Test
    void tailorResume_returns400_whenJobNotFound() {
        UserConfig config = new UserConfig();
        Resume base = new Resume();
        when(userConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(resumeRepository.findFirstByUserConfigAndIsActive(config, true)).thenReturn(Optional.of(base));
        when(jobRepository.findById(42L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = resumeController.tailorResume(Map.of("userId", 1, "jobId", 42));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(resumeTailor, never()).tailorAndSave(any(), any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=ResumeControllerTest -q 2>&1 | tail -20
```

Expected: FAIL — `ResumeController` class doesn't exist.

- [ ] **Step 3: Create `ResumeController.java`**

Create `src/main/java/com/jobbot/controller/ResumeController.java`:

```java
package com.jobbot.controller;

import com.jobbot.entity.Job;
import com.jobbot.entity.Resume;
import com.jobbot.entity.UserConfig;
import com.jobbot.repository.JobRepository;
import com.jobbot.repository.ResumeRepository;
import com.jobbot.repository.UserConfigRepository;
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
public class ResumeController {

    private static final Logger logger = LoggerFactory.getLogger(ResumeController.class);

    @Autowired
    private UserConfigRepository userConfigRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeTailor resumeTailor;

    @PostMapping("/tailor")
    public ResponseEntity<?> tailorResume(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Long jobId = Long.valueOf(body.get("jobId").toString());

        Optional<UserConfig> configOpt = userConfigRepository.findById(userId);
        if (configOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found: " + userId));
        }

        Optional<Resume> baseResumeOpt = resumeRepository.findFirstByUserConfigAndIsActive(configOpt.get(), true);
        if (baseResumeOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No active base resume found for user " + userId));
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

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=ResumeControllerTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jobbot/controller/ResumeController.java \
        src/test/java/com/jobbot/controller/ResumeControllerTest.java
git commit -m "feat: add ResumeController with POST /api/resumes/tailor endpoint

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 7: Integrate `ResumeTailor` into `SchedulerService`

**Files:**
- Modify: `src/main/java/com/jobbot/service/SchedulerService.java`
- Test: `src/test/java/com/jobbot/service/SchedulerServiceTest.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/jobbot/service/SchedulerServiceTest.java`:

```java
package com.jobbot.service;

import com.jobbot.entity.Job;
import com.jobbot.entity.Resume;
import com.jobbot.entity.UserConfig;
import com.jobbot.repository.JobRepository;
import com.jobbot.repository.ResumeRepository;
import com.jobbot.repository.UserConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerServiceTest {

    @Mock
    private LinkedInJobFetcher jobFetcher;

    @Mock
    private JobMatcher jobMatcher;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserConfigRepository userConfigRepository;

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private ResumeTailor resumeTailor;

    @InjectMocks
    private SchedulerService schedulerService;

    private UserConfig config;
    private Resume baseResume;
    private Job matchedJob;

    @BeforeEach
    void setup() {
        config = new UserConfig();
        config.setJobKeywords("Java");
        config.setYearsExperienceMax(3);
        config.setLocation("Bangalore");

        baseResume = new Resume();
        baseResume.setId(1L);

        matchedJob = new Job();
        matchedJob.setId(10L);
        matchedJob.setTitle("SWE");
    }

    @Test
    void executeRun_tailorsResumeForEachMatchedJob() {
        when(userConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(jobFetcher.searchJobs(any(), anyString(), anyInt(), anyString()))
            .thenReturn(List.of(matchedJob));
        when(jobMatcher.filterJobs(any(), any())).thenReturn(List.of(matchedJob));
        when(resumeRepository.findFirstByUserConfigAndIsActive(config, true))
            .thenReturn(Optional.of(baseResume));
        when(resumeTailor.tailorAndSave(matchedJob, baseResume)).thenReturn(Optional.of(new Resume()));

        Map<String, Object> result = schedulerService.executeRun(1L);

        assertEquals("success", result.get("status"));
        assertEquals(1, result.get("jobsMatched"));
        assertEquals(0, result.get("tailoringErrors"));
        verify(resumeTailor).tailorAndSave(matchedJob, baseResume);
    }

    @Test
    void executeRun_whenNoActiveResume_skipsAllTailoring() {
        when(userConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(jobFetcher.searchJobs(any(), anyString(), anyInt(), anyString()))
            .thenReturn(List.of(matchedJob));
        when(jobMatcher.filterJobs(any(), any())).thenReturn(List.of(matchedJob));
        when(resumeRepository.findFirstByUserConfigAndIsActive(config, true))
            .thenReturn(Optional.empty());

        Map<String, Object> result = schedulerService.executeRun(1L);

        assertEquals("success", result.get("status"));
        verify(resumeTailor, never()).tailorAndSave(any(), any());
    }

    @Test
    void executeRun_tailoringFailure_incrementsErrorCount() {
        when(userConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(jobFetcher.searchJobs(any(), anyString(), anyInt(), anyString()))
            .thenReturn(List.of(matchedJob));
        when(jobMatcher.filterJobs(any(), any())).thenReturn(List.of(matchedJob));
        when(resumeRepository.findFirstByUserConfigAndIsActive(config, true))
            .thenReturn(Optional.of(baseResume));
        when(resumeTailor.tailorAndSave(matchedJob, baseResume)).thenReturn(Optional.empty());

        Map<String, Object> result = schedulerService.executeRun(1L);

        assertEquals("success", result.get("status"));
        assertEquals(1, result.get("tailoringErrors"));
    }

    @Test
    void executeRun_whenUserNotFound_returnsFailure() {
        when(userConfigRepository.findById(99L)).thenReturn(Optional.empty());

        Map<String, Object> result = schedulerService.executeRun(99L);

        assertEquals("failed", result.get("status"));
        verify(resumeTailor, never()).tailorAndSave(any(), any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -Dtest=SchedulerServiceTest -q 2>&1 | tail -20
```

Expected: FAIL — `SchedulerService` has no `ResumeTailor` or `ResumeRepository` dependency.

- [ ] **Step 3: Update `SchedulerService.java`**

Replace the full contents of `src/main/java/com/jobbot/service/SchedulerService.java`:

```java
package com.jobbot.service;

import com.jobbot.entity.Job;
import com.jobbot.entity.Resume;
import com.jobbot.entity.UserConfig;
import com.jobbot.repository.JobRepository;
import com.jobbot.repository.ResumeRepository;
import com.jobbot.repository.UserConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerService.class);

    @Autowired
    private LinkedInJobFetcher jobFetcher;

    @Autowired
    private JobMatcher jobMatcher;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserConfigRepository userConfigRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeTailor resumeTailor;

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
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -Dtest=SchedulerServiceTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, 4 tests passed.

- [ ] **Step 5: Run all tests to check nothing is broken**

```bash
mvn test -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jobbot/service/SchedulerService.java \
        src/test/java/com/jobbot/service/SchedulerServiceTest.java
git commit -m "feat: integrate ResumeTailor into SchedulerService pipeline

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 8: Final verification

- [ ] **Step 1: Compile the full project**

```bash
mvn clean compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS with no warnings about missing beans.

- [ ] **Step 2: Run all tests**

```bash
mvn test 2>&1 | grep -E "(Tests run|BUILD|FAIL|ERROR)" | tail -20
```

Expected: All test classes pass, BUILD SUCCESS.

- [ ] **Step 3: Verify new files exist**

```bash
find src -name "*.java" | sort
```

Expected output includes:
```
src/main/java/com/jobbot/config/ClaudeApiConfig.java
src/main/java/com/jobbot/controller/ResumeController.java
src/main/java/com/jobbot/service/ClaudeApiClient.java
src/main/java/com/jobbot/service/ResumeTailor.java
src/test/java/com/jobbot/config/ClaudeApiConfigTest.java
src/test/java/com/jobbot/controller/ResumeControllerTest.java
src/test/java/com/jobbot/entity/ResumeEntityTest.java
src/test/java/com/jobbot/repository/ResumeRepositoryTest.java
src/test/java/com/jobbot/service/ClaudeApiClientTest.java
src/test/java/com/jobbot/service/ResumeTailorTest.java
src/test/java/com/jobbot/service/SchedulerServiceTest.java
```

- [ ] **Step 4: Final commit with updated docs**

```bash
git add docs/superpowers/specs/2026-05-06-phase2-resume-tailoring-design.md \
        docs/superpowers/plans/2026-05-06-phase2-resume-tailoring.md
git commit -m "docs: add Phase 2 resume tailoring spec and implementation plan

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```
