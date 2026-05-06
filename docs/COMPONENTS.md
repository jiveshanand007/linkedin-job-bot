# Component Details - LinkedIn Job Apply Bot

## Service Components

### 1. LinkedInJobFetcher

**File:** `src/main/java/com/jobbot/service/LinkedInJobFetcher.java`

**Responsibility:** Search LinkedIn for jobs matching user criteria

**Phase Status:** Phase 3a - ✅ COMPLETE (Playwright-based automation with SearchConfig integration)

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

### 1b. PlaywrightSessionManager (Phase 3a - NEW)

**File:** `src/main/java/com/jobbot/service/PlaywrightSessionManager.java`

**Responsibility:** Manage browser lifecycle, LinkedIn login, and session state

**Phase Status:** Phase 3a - ✅ COMPLETE

**Methods:**
```java
void initialize() throws Exception
void login(String email, String password) throws Exception
BrowserContext getBrowserContext()
void close()
```

**What It Does:**
1. Initializes Playwright + Chromium browser
2. Handles LinkedIn authentication
3. Maintains session across multiple searches
4. Gracefully closes browser on shutdown

**Inputs:**
- LinkedIn email and encrypted password (from UserConfig)

**Outputs:**
- Active BrowserContext for job searching
- Maintains login state across operations

**Dependencies:**
- Playwright (browser automation)
- UserConfig (credentials)

---

### 1c. JobParser (Phase 3a - NEW)

**File:** `src/main/java/com/jobbot/service/JobParser.java`

**Responsibility:** Stateless parsing of DOM elements to Job entities

**Phase Status:** Phase 3a - ✅ COMPLETE

**Methods:**
```java
Job parseJobCard(JobCardData cardData)
String normalizeSalary(String salaryText) // returns salary in LPA
```

**What It Does:**
1. Accepts JobCardData (extracted from LinkedIn DOM)
2. Maps DOM elements to Job entity fields
3. Normalizes salary (e.g., "₹35,00,000" → 35 LPA)
4. Detects application type (Easy Apply vs external)
5. Returns fully formed Job object

**Dependencies:**
- JobCardData (intermediate data structure)

---

### 1d. JobCardData (Phase 3a - NEW)

**File:** `src/main/java/com/jobbot/entity/JobCardData.java`

**Responsibility:** Plain Java record representing raw DOM extraction

**Phase Status:** Phase 3a - ✅ COMPLETE

**Fields:**
```java
record JobCardData(
    String linkedInJobId,
    String title,
    String company,
    String salaryText,
    String description,
    String applicationType, // "easy_apply" or "external"
    String url,
    String location
)
```

**Purpose:**
- Boundary between Playwright/DOM extraction and JobParser
- Immutable, serializable
- Makes DOM extraction logic independent of Job entity

**Dependencies:**
- None (no database dependencies)

---

### 1e. SearchConfig (Phase 3a - NEW)

**File:** `src/main/java/com/jobbot/entity/SearchConfig.java`

**Responsibility:** JPA entity for per-user job search filters

**Phase Status:** Phase 3a - ✅ COMPLETE

**Fields:**
```java
@Entity
public class SearchConfig {
    @Id
    @GeneratedValue
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "user_config_id", unique = true)
    private UserConfig userConfig;
    
    @Column(columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean remoteOnly;
    
    @Column(columnDefinition = "TEXT")
    private String experienceLevel; // ENTRY, MID, SENIOR, DIRECTOR, NULL
    
    @Column(columnDefinition = "TEXT DEFAULT 'ANY'")
    private String datePostedFilter; // PAST_DAY, PAST_WEEK, PAST_MONTH, ANY
    
    @Column(columnDefinition = "INTEGER DEFAULT 3")
    private Integer maxPages; // [1..10]
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Dependencies:**
- UserConfig (1-to-1 relationship)

---

### 1f. SearchConfigController (Phase 3a - NEW)

**File:** `src/main/java/com/jobbot/controller/SearchConfigController.java`

**Responsibility:** REST CRUD endpoints for SearchConfig

**Phase Status:** Phase 3a - ✅ COMPLETE

**Endpoints:**
```
POST   /api/search-config              Create search config for user
PUT    /api/search-config/{id}         Full-replace update (null = reset to default)
GET    /api/search-config/user/{id}    Get search config by user ID
DELETE /api/search-config/{id}         Delete search config
```

**Example Request/Response:**
```json
POST /api/search-config
{
  "userConfigId": 1,
  "remoteOnly": true,
  "experienceLevel": "MID",
  "datePostedFilter": "PAST_WEEK",
  "maxPages": 5
}

Response (200):
{
  "id": 1,
  "userConfigId": 1,
  "remoteOnly": true,
  "experienceLevel": "MID",
  "datePostedFilter": "PAST_WEEK",
  "maxPages": 5,
  "createdAt": "2026-04-22T21:45:00"
}
```

**Dependencies:**
- SearchConfigRepository
- UserConfigRepository

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

### 4a. LaTeXCompiler (Phase 3b - COMPLETE ✅)

**File:** `src/main/java/com/jobbot/service/LaTeXCompiler.java`

**Type:** `@Component` (Spring-managed utility)

**Responsibility:** Compile LaTeX resume content to PDF using pdflatex

**Methods:**
```java
File compileToPdf(String latexContent, Long resumeId) throws Exception
```

**Inputs:**
- `latexContent` - Full LaTeX resume source code
- `resumeId` - Resume ID for file naming

**Outputs:**
- `File` — Path to generated PDF on success

**What It Does:**
1. Write LaTeX content to temporary `.tex` file
2. Run pdflatex in 2-pass mode (ensures TOC, references, syntax)
3. Extract and move PDF to persistent storage (`/pdfs/resume_{resumeId}.pdf`)
4. Clean up temporary files
5. Return File reference or throw exception on failure

**Dependencies:**
- System: pdflatex (must be installed)
- File I/O utilities

**Installation Required:**
- Linux: `sudo apt install texlive-latex-base`
- macOS: `brew install --cask mactex`
- Windows: MiKTeX or TeX Live

---

### 4b. PlaywrightApplicationSession (Phase 3b - COMPLETE ✅)

**File:** `src/main/java/com/jobbot/entity/PlaywrightApplicationSession.java`

**Type:** Plain Java class (not Spring-managed)

**Responsibility:** Encapsulate Easy Apply form automation logic

**Methods:**
```java
void fillEasyApplyForm(BrowserContext context, Job job, File pdfPath, String coverLetter)
ApplicationResult submitForm()
```

**Inputs:**
- `context` - Playwright BrowserContext (from PlaywrightSessionManager)
- `job` - Job with application URL
- `pdfPath` - Path to compiled resume PDF
- `coverLetter` - Generated cover letter text

**Outputs:**
- `ApplicationResult` with status (success/failed) and error details

**What It Does:**
1. Navigates to Easy Apply button on job listing
2. Detects form fields (name, email, phone, etc.)
3. Auto-fills common fields from UserConfig
4. Uploads resume PDF
5. Inserts cover letter (if text field exists)
6. Detects required vs optional fields
7. Submits form and verifies success page

**Field Detection:**
- Standard fields: name, email, phone, location
- Optional fields: experience, cover letter, attachments
- Fallback to Claude API for unknown required fields

**Dependencies:**
- Playwright (browser automation)
- ApplicationResult (result object)

---

### 4c. ApplicationSubmitter (Phase 3b - COMPLETE ✅)

**File:** `src/main/java/com/jobbot/service/ApplicationSubmitter.java`

**Type:** `@Component` (Spring-managed service)

**Responsibility:** Orchestrate end-to-end application submission

**Methods:**
```java
ApplicationResult submitApplication(Job job, Resume tailoredResume, String coverLetter)
```

**Inputs:**
- `job` - Job to apply to (with application_type field)
- `tailoredResume` - Resume entity with LaTeX content
- `coverLetter` - Generated cover letter text

**Outputs:**
- `ApplicationResult` with:
  - `status` - "success" or "failed"
  - `errorReason` - If failed
  - `applicationResponse` - Form confirmation (if available)

**Execution Pipeline:**
```
1. Call LaTeXCompiler.compileToPdf(tailoredResume.latexContent)
2. If Easy Apply:
   - Create PlaywrightApplicationSession
   - Fill form fields
   - Upload PDF
   - Insert cover letter
   - Submit
3. Else (External form):
   - Navigate to company form
   - Auto-fill common fields
   - Upload PDF
   - Detect unknowns, use Claude if needed
   - Submit
4. Save Application record to database
5. Return ApplicationResult
```

**Dependencies:**
- LaTeXCompiler
- PlaywrightApplicationSession
- PlaywrightSessionManager
- ApplicationRepository
- ResumeRepository
- ClaudeApiClient (for field detection)

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
  "location": "Remote,Bangalore",
  "phoneNumber": "+91-9876543210"
}

Response: { id: 1, email, phoneNumber, ... }
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

Response: { id: 1, email, linkedInEmail, jobKeywords, phoneNumber, ... }
```

**Error Handling:**
- 400: Invalid input or user not found
- 500: Database error

---

### 2. SchedulerController

**File:** `src/main/java/com/jobbot/controller/SchedulerController.java`

**Phase Status:** Phase 1 - Partial (manual trigger) / Phase 4 - Complete (@Scheduled support)

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
  "message": "Hourly scheduler running",
  "nextRun": "2026-04-22T22:00:00"
}
```
(Phase 4: Now uses @Scheduled annotation)

#### Stop Scheduler
```
POST /api/scheduler/stop?userId=1

Response: { "status": "stopped" }
```
(Phase 4: Disables scheduler for user)

#### Get Scheduler Status
```
GET /api/scheduler/status?userId=1

Response: {
  "schedulerActive": true,
  "autoApplyEnabled": false,
  "userId": 1,
  "nextRun": "2026-04-22T22:00:00"
}
```
(Phase 4: New endpoint for status monitoring)

**Error Handling:**
- 400: User not found
- 500: Execution error (logged with details)

---

### 3. ApplicationController (Phase 3b - COMPLETE ✅)

**File:** `src/main/java/com/jobbot/controller/ApplicationController.java`

**Phase Status:** Phase 3b - COMPLETE

**Endpoints:**

#### Get Application History
```
GET /api/applications/user/{userId}?limit=20&offset=0

Response: [
  {
    "id": 1,
    "jobId": 1,
    "jobTitle": "Senior Java Developer",
    "company": "TechCorp",
    "status": "success",
    "submittedAt": "2026-04-22T21:46:00",
    "errorReason": null
  },
  {
    "id": 2,
    "jobId": 2,
    "jobTitle": "Backend Engineer",
    "company": "StartupXYZ",
    "status": "failed",
    "submittedAt": null,
    "errorReason": "Form submission timeout"
  }
]
```

#### Get Single Application
```
GET /api/applications/{applicationId}

Response: {
  "id": 1,
  "jobId": 1,
  "resumeId": 10,
  "status": "success",
  "errorReason": null,
  "generatedPdfPath": "/pdfs/resume_abc123.pdf",
  "coverLetter": "Dear Hiring Manager...",
  "submittedAt": "2026-04-22T21:46:00",
  "createdAt": "2026-04-22T21:46:00"
}
```

**Query Parameters:**
- `limit` (optional, default: 20) - Number of records to return
- `offset` (optional, default: 0) - Pagination offset
- `status` (optional) - Filter by status: success, failed, pending

**Response Fields:**
- `id` - Application record ID
- `jobId` - Linked job ID
- `status` - "success", "failed", or "pending"
- `submittedAt` - When application was submitted
- `errorReason` - If status is "failed"

**Error Handling:**
- 404: User not found
- 400: Invalid query parameters
- 500: Database error

**Common Filters:**
```bash
# Get last 10 successful applications
GET /api/applications/user/1?status=success&limit=10

# Get failed applications for debugging
GET /api/applications/user/1?status=failed&limit=50
```

---

### 4. SearchConfigController

**File:** `src/main/java/com/jobbot/controller/SearchConfigController.java`

**Phase Status:** Phase 3a - COMPLETE ✅

**Endpoints:**

#### Create Search Config
```
POST /api/search-config

{
  "userConfigId": 1,
  "remoteOnly": true,
  "experienceLevel": "MID",
  "datePostedFilter": "PAST_WEEK",
  "maxPages": 5
}

Response: { id: 1, userConfigId: 1, ... }
```

#### Update Search Config
```
PUT /api/search-config/{id}

{
  "remoteOnly": false,
  "experienceLevel": "SENIOR"
}

Response: { id: 1, userConfigId: 1, ... }
```

#### Get Search Config by User
```
GET /api/search-config/user/{userId}

Response: { id: 1, userConfigId: 1, remoteOnly: true, ... }
```

#### Delete Search Config
```
DELETE /api/search-config/{id}

Response: 204 No Content
```

**Error Handling:**
- 400: Invalid input
- 404: Not found
- 500: Database error

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
| LinkedInJobFetcher | 3a | ✅ Complete | Search LinkedIn |
| PlaywrightSessionManager | 3a | ✅ Complete | Browser lifecycle & login |
| JobParser | 3a | ✅ Complete | DOM-to-Job mapping |
| SearchConfig | 3a | ✅ Complete | Per-user search filters |
| SearchConfigController | 3a | ✅ Complete | Search config CRUD API |
| JobMatcher | 1 | ✅ Complete | Filter jobs |
| SchedulerService | 1,2,4 | ✅ Updated | Orchestrate pipeline + @Scheduled support |
| ClaudeApiClient | 2 | ✅ Complete | Anthropic API HTTP client |
| ResumeTailor | 2 | ✅ Complete | AI resume tailoring |
| LaTeXCompiler | 3b | ✅ Complete | LaTeX→PDF compilation via pdflatex |
| PlaywrightApplicationSession | 3b | ✅ Complete | Easy Apply form automation |
| ApplicationSubmitter | 3b | ✅ Complete | Orchestrate submission pipeline |
| ConfigController | 1 | ✅ Complete | Setup API |
| ResumeController | 2 | ✅ Complete | Tailor API |
| SchedulerController | 1,4 | ✅ Complete | Trigger + Scheduler status API |
| ApplicationController | 3b | ✅ Complete | Application history API |
| 6 Entities | 1,2,3a | ✅ Updated | Data models |
| 6 Repositories | 1,2,3a | ✅ Updated | Data access |

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
