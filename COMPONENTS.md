# Component Details - LinkedIn Job Apply Bot

## Service Components

### 1. LinkedInJobFetcher

**File:** `src/main/java/com/jobbot/service/LinkedInJobFetcher.java`

**Responsibility:** Search LinkedIn for jobs matching user criteria

**Phase Status:** Phase 1 - Placeholder only (full implementation Phase 2)

**Methods:**
```java
List<Job> searchJobs(UserConfig config, String keywords, int yearsMax, String location)
```

**Inputs:**
- `config` - User's LinkedIn credentials & preferences
- `keywords` - CSV keywords to search (e.g., "Java,Spring")
- `yearsMax` - Max years of experience to filter (e.g., 3)
- `location` - Job location filter (e.g., "Remote,Bangalore")

**Outputs:**
- `List<Job>` - Job listings with extracted details:
  - Title, Company, Description
  - Salary (if available)
  - Application type (easy_apply or external)
  - LinkedIn job URL

**Phase 2 Implementation Plan:**
1. Initialize Playwright browser with Chromium
2. Navigate to LinkedIn
3. Login with credentials
4. Search for jobs with filters
5. Extract job listings from DOM
6. Parse salary (convert to LPA)
7. Detect application type
8. Return Job objects

**Dependencies:**
- Playwright (browser automation)
- UserConfig (credentials)

---

### 2. JobMatcher

**File:** `src/main/java/com/jobbot/service/JobMatcher.java`

**Responsibility:** Filter jobs based on user criteria

**Phase Status:** Phase 1 - COMPLETE ✅

**Methods:**
```java
List<Job> filterJobs(List<Job> jobs, UserConfig config)
```

**Inputs:**
- `jobs` - Raw list of jobs from LinkedIn
- `config` - User criteria:
  - `minSalaryLPA` - Minimum salary filter
  - `jobKeywords` - Required keywords (CSV)
  - `blacklistKeywords` - Exclude keywords (CSV)

**Outputs:**
- `List<Job>` - Filtered jobs matching all criteria

**Filtering Logic:**
1. **Deduplication:** Skip if already applied (check ApplicationRepository)
2. **Salary:** Skip if salary < minSalaryLPA
3. **Keywords:** Skip if required keywords missing from title+description
4. **Blacklist:** Skip if any blacklist keyword present
5. **Result:** Only jobs passing all filters

**Example:**
```
Input: 50 jobs from LinkedIn
- Filter by salary (30 LPA): 35 pass
- Filter by keywords ("Java"): 20 pass
- Filter by blacklist ("Manager"): 18 pass
- Skip already applied: 15 pass
Output: 15 matching jobs
```

**Dependencies:**
- ApplicationRepository (to check already-applied jobs)
- Logger (for debugging)

---

### 3. SchedulerService

**File:** `src/main/java/com/jobbot/service/SchedulerService.java`

**Responsibility:** Orchestrate the complete job search → match → apply pipeline

**Phase Status:** Phase 1 - Partial (orchestration) / Phase 3+ (application)

**Methods:**
```java
Map<String, Object> executeRun(Long userId)
```

**Inputs:**
- `userId` - ID of user to run bot for

**Outputs:**
- `Map<String, Object>` containing:
  - `status` - "success" or "failed"
  - `jobsFetched` - Count of jobs found
  - `jobsMatched` - Count matching criteria
  - `applicationsSubmitted` - Count applied (Phase 3+)
  - `error` - Error message if failed

**Execution Pipeline:**
```
1. Load UserConfig from database
2. Call LinkedInJobFetcher.searchJobs()
3. Call JobMatcher.filterJobs()
4. Save matched jobs to database
5. [Phase 3] For each job: generate resume, apply
6. [Phase 4] Log results to AuditLog
7. Return execution summary
```

**Phase 1 Implementation:** Executes steps 1-4  
**Phase 3 Implementation:** Adds steps 5-6  

**Error Handling:**
- If user not found: throws RuntimeException
- If LinkedIn search fails: returns empty list (handled by LinkedInJobFetcher)
- If database error: throws exception to controller

**Dependencies:**
- LinkedInJobFetcher
- JobMatcher
- JobRepository
- UserConfigRepository
- ResumeRepository
- ResumeTailor ✅ (Phase 2)
- ApplicationSubmitter (Phase 3)

---

### 4. ResumeTailor (Phase 2 - COMPLETE ✅)

**File:** `src/main/java/com/jobbot/service/ResumeTailor.java`

**Responsibility:** Tailor a base LaTeX resume to a specific job using Claude API, persist the result.

**Methods:**
```java
Optional<Resume> tailorAndSave(Job job, Resume baseResume)
```

**Inputs:**
- `job` - Job with title, company, jobDescription
- `baseResume` - Active base LaTeX resume from database

**Outputs:**
- `Optional<Resume>` — populated with the saved tailored resume on success, empty on failure

**What It Does:**
1. Sends base LaTeX + job details to Claude via `ClaudeApiClient`
2. Claude rewrites summary, skills list, and experience bullets to match the JD
3. Saves tailored LaTeX as a new `Resume` row (`isActive=false`, `parentResumeId` set)
4. Returns the saved entity; returns `Optional.empty()` on any failure (non-throwing)

**Dependencies:**
- ClaudeApiClient
- ResumeRepository

---

### 5. ApplicationSubmitter (Phase 3 - NOT YET IMPLEMENTED)

**File:** `src/main/java/com/jobbot/service/ApplicationSubmitter.java` (to be created)

**Responsibility:** Submit applications to jobs with tailored resumes

**Planned Methods:**
```java
ApplicationResult submitApplication(Job job, Resume tailoredResume, String coverLetter)
```

**Inputs:**
- `job` - Job to apply to (with application_type field)
- `tailoredResume` - Generated tailored resume PDF
- `coverLetter` - Generated cover letter text

**Outputs:**
- `ApplicationResult` with:
  - `status` - "success" or "failed"
  - `errorReason` - If failed
  - `applicationResponse` - Form confirmation (if available)

**Phase 3 Implementation Plan:**
1. **If Easy Apply:**
   - Use Playwright to click "Apply" button
   - Auto-detect form fields
   - Fill fields with extracted data
   - Upload tailored PDF
   - Insert cover letter
   - Submit form
   - Verify success

2. **If External:**
   - Navigate to company job form
   - Auto-fill common fields (name, email, phone)
   - Upload PDF
   - Detect unknown fields
   - Use Claude to intelligently fill unknowns
   - Submit

**Dependencies:**
- Playwright
- Claude API (for field detection)
- ApplicationRepository

---

## Controller Components

### 1. ConfigController

**File:** `src/main/java/com/jobbot/controller/ConfigController.java`

**Phase Status:** Phase 1 - COMPLETE ✅

**Endpoints:**

#### Setup Configuration
```
POST /api/config/setup
Content-Type: application/json

{
  "email": "user@example.com",
  "linkedInEmail": "user@linkedin.com",
  "linkedInPasswordEncrypted": "encrypted_pwd",
  "jobKeywords": "Java,Spring,Backend",
  "blacklistKeywords": "Manager,Director",
  "minSalaryLPA": 30,
  "yearsExperienceMax": 3,
  "location": "Remote,Bangalore"
}

Response: { id: 1, email, ... }
```

#### Upload Resume
```
POST /api/config/resumes/upload?userId=1
Content-Type: application/json

{
  "versionName": "v1",
  "latexContent": "\\documentclass{article}..."
}

Response: { id: 1, versionName, uploadedAt, ... }
```

#### List Resumes
```
GET /api/config/resumes/1

Response: [
  { id: 1, versionName: "v1", uploadedAt: "...", isActive: true },
  { id: 2, versionName: "v2", uploadedAt: "...", isActive: false }
]
```

#### Get Configuration
```
GET /api/config/1

Response: { id: 1, email, linkedInEmail, jobKeywords, ... }
```

**Error Handling:**
- 400: Invalid input or user not found
- 500: Database error

---

### 2. SchedulerController

**File:** `src/main/java/com/jobbot/controller/SchedulerController.java`

**Phase Status:** Phase 1 - Partial (manual trigger only)

**Endpoints:**

#### Manual Job Search Trigger
```
POST /api/scheduler/run?userId=1

Response: {
  "status": "success",
  "jobsFetched": 50,
  "jobsMatched": 15,
  "applicationsSubmitted": 0,
  "timestamp": "2026-04-22T21:45:00"
}
```

#### Start Hourly Scheduler
```
POST /api/scheduler/start?userId=1

Response: {
  "status": "started",
  "message": "Hourly scheduler will start in Phase 4"
}
```
(Phase 4 implementation)

#### Stop Scheduler
```
POST /api/scheduler/stop?userId=1

Response: { "status": "stopped" }
```
(Phase 4 implementation)

**Error Handling:**
- 400: User not found
- 500: Execution error (logged with details)

---

## Data Access Layer (Repositories)

### UserConfigRepository
```java
Optional<UserConfig> findByEmail(String email)
findById(Long id)
save(UserConfig)
```

### JobRepository
```java
Optional<Job> findByUserConfigAndLinkedInJobId(UserConfig, String jobId)
List<Job> findByUserConfig(UserConfig)
save(Job)
```

### ApplicationRepository
```java
Optional<Application> findByJob(Job)
List<Application> findByStatus(String status)
save(Application)
```

### ResumeRepository
```java
List<Resume> findByUserConfig(UserConfig)
List<Resume> findByUserConfigAndIsActive(UserConfig, Boolean)
save(Resume)
```

### AuditLogRepository
```java
List<AuditLog> findByUserConfigOrderByTimestampDesc(UserConfig)
save(AuditLog)
```

---

## Summary Table

| Component | Phase | Status | Purpose |
|-----------|-------|--------|---------|
| LinkedInJobFetcher | 3 | Stub | Search LinkedIn |
| JobMatcher | 1 | ✅ Complete | Filter jobs |
| SchedulerService | 1,2 | ✅ Updated | Orchestrate pipeline |
| ClaudeApiClient | 2 | ✅ Complete | Anthropic API HTTP client |
| ResumeTailor | 2 | ✅ Complete | AI resume tailoring |
| ApplicationSubmitter | 3 | TODO | Submit applications |
| ConfigController | 1 | ✅ Complete | Setup API |
| ResumeController | 2 | ✅ Complete | Tailor API |
| SchedulerController | 1,4 | Partial | Trigger API |
| 5 Entities | 1,2 | ✅ Updated | Data models |
| 5 Repositories | 1,2 | ✅ Updated | Data access |

---

## Dependencies Between Components

```
User Request
    ↓
ConfigController / SchedulerController
    ↓
SchedulerService
    ├─→ LinkedInJobFetcher → jobs
    ├─→ JobMatcher → filtered jobs
    ├─→ [Phase 3] ResumeTailor → tailored resume
    ├─→ [Phase 3] ApplicationSubmitter → apply
    └─→ Repositories → database

All results logged via AuditLogger
```
