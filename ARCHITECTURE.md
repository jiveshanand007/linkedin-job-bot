# System Architecture - LinkedIn Job Apply Bot

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot Backend (Port 8080)              │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ REST Controllers                                        │  │
│  │  - ConfigController (setup, resume management)         │  │
│  │  - SchedulerController (trigger runs, scheduling)      │  │
│  └────────────────────┬────────────────────────────────────┘  │
│                       │ HTTP Requests                          │
│  ┌────────────────────▼────────────────────────────────────┐  │
│  │ Service Layer                                           │  │
│  │  - SchedulerService (orchestration)                    │  │
│  │  - LinkedInJobFetcher (Playwright automation)          │  │
│  │  - JobMatcher (filtering logic)                        │  │
│  │  - [Phase 2] ResumeTailor (Claude + LaTeX)            │  │
│  │  - [Phase 3] ApplicationSubmitter (form filling)       │  │
│  └────────────┬──────────────────────────────┬────────────┘  │
│               │ JPA Queries                  │ API Calls      │
│  ┌────────────▼──────────────────────────┐   │                │
│  │ Repository Layer (Data Access)       │   │                │
│  │  - UserConfigRepository              │   │                │
│  │  - ResumeRepository                  │   │                │
│  │  - JobRepository                     │   │                │
│  │  - ApplicationRepository             │   │                │
│  │  - AuditLogRepository                │   │                │
│  └────────────┬──────────────────────────┘   │                │
│               │ SQL Queries                  │                │
│  ┌────────────▼──────────────────────────┐   │                │
│  │ SQLite Database                      │   │                │
│  │  - user_config                       │   │                │
│  │  - resumes                           │   │                │
│  │  - jobs                              │   │                │
│  │  - applications                      │   │                │
│  │  - audit_logs                        │   │                │
│  └──────────────────────────────────────┘   │                │
│                                             │                │
│  ┌──────────────────────────────────────┐   │                │
│  │ External Integrations                │   │                │
│  │  - [Phase 2] Playwright (LinkedIn)   │   │                │
│  │  - Claude API (resume tailoring)     ◄───┤ Optional calls │
│  │  - LaTeX compiler (PDF generation)   │   │                │
│  └──────────────────────────────────────┘   │                │
└─────────────────────────────────────────────────────────────────┘
```

---

## Component Interactions

### When User Triggers Job Search

```
Client (curl/Postman)
    ↓
    POST /api/scheduler/run?userId=1
    ↓
ConfigController.manualRun()
    ↓
SchedulerService.executeRun(userId)
    ↓
    ├─→ LinkedInJobFetcher.searchJobs()
    │   ├─ [Phase 2] Playwright login
    │   ├─ [Phase 2] Navigate to jobs
    │   └─ Extract job listings → List<Job>
    │
    ├─→ JobMatcher.filterJobs(jobs, config)
    │   ├─ Check salary >= minSalaryLPA
    │   ├─ Check required keywords present
    │   ├─ Check no blacklist keywords
    │   └─ Skip already-applied jobs → Filtered List<Job>
    │
    ├─→ JobRepository.saveAll(matchedJobs)
    │   └─ Persist to SQLite
    │
    └─→ Return result { jobsFetched, jobsMatched, etc }
    ↓
Response sent to client
```

---

## Phase-by-Phase Implementation

### Phase 1: Core Backend ✅ DONE
- Entities, repositories, basic services
- Config & scheduler REST endpoints
- Job matching logic

### Phase 2: Resume Tailoring 🔄 TODO
```
LinkedInJobFetcher finds job with JD
    ↓
ResumeTailor.generateTailoredResume(baseResume, job)
    ├─ Parse base LaTeX resume
    ├─ Call Claude API with JD
    ├─ Claude returns tailored LaTeX (P&C selection)
    ├─ LaTeXCompiler.compileToPdf() via pdflatex
    └─ Store PDF path in Application entity
```

### Phase 3: Application Submission 🔄 TODO
```
For each matched job:
    ├─ Check application type (easy_apply vs external)
    │
    ├─ If Easy Apply:
    │   ├─ Playwright clicks "Apply"
    │   ├─ Auto-fills form fields
    │   ├─ Uploads tailored PDF
    │   ├─ Inserts cover letter
    │   └─ Submits
    │
    └─ If External:
        ├─ Open company job form
        ├─ Auto-fill common fields (name, email)
        ├─ Upload PDF
        ├─ Claude identifies unknown fields
        ├─ Claude suggests answers
        └─ Submit if confident
```

### Phase 4: Scheduling 🔄 TODO
```
Quartz Scheduler
    ├─ Hourly trigger
    ├─ On-demand via /api/scheduler/run
    └─ Calls SchedulerService.executeRun() → full pipeline
```

### Phase 5: Testing & Deployment 🔄 TODO
- Unit tests for each service
- Integration tests for API endpoints
- Deploy to Railway/Render
- Database migration (SQLite → PostgreSQL if needed)

---

## Data Flow: Complete Pipeline (End-to-End)

```
User Setup
    ↓
POST /api/config/setup
    └─ UserConfig saved with:
       - LinkedIn credentials
       - Job keywords (e.g., "Java, Spring")
       - Salary minimum (e.g., 30 LPA)
       - Years of experience max (e.g., 3)
    ↓
POST /api/config/resumes/upload
    └─ Resume saved as:
       - versionName: "v1"
       - latexContent: Full LaTeX code
    ↓
════════════════════════════════════════════════════════════════════
Automated Job Search & Application Cycle
════════════════════════════════════════════════════════════════════
    ↓
[Every hour OR on-demand]
    ↓
POST /api/scheduler/run?userId=1
    ├─ LinkedInJobFetcher searches LinkedIn
    │  └─ Returns 50 jobs matching criteria
    │
    ├─ JobMatcher filters jobs
    │  ├─ Salary >= 30 LPA? ✓
    │  ├─ "Java" in title/description? ✓
    │  ├─ No "Manager" in title? ✓
    │  └─ Already applied? ✗
    │  └─ Result: 15 matching jobs
    │
    ├─ [Phase 3] For each matched job:
    │  ├─ ResumeTailor generates tailored resume
    │  │  ├─ Claude analyzes JD
    │  │  ├─ Selects relevant projects (P&C)
    │  │  ├─ Generates LaTeX
    │  │  ├─ Compiles to PDF
    │  │  └─ Saves PDF path
    │  │
    │  ├─ ResumeTailor generates cover letter
    │  │  ├─ Claude writes personalized letter
    │  │  └─ Saves to Application entity
    │  │
    │  ├─ ApplicationSubmitter applies
    │  │  ├─ Easy Apply? Use Playwright
    │  │  ├─ External? Intelligent form filling
    │  │  └─ Mark application as "success" or "failed"
    │  │
    │  └─ AuditLogger records:
    │     ├─ job_id, company, status
    │     ├─ resume_version_hash
    │     ├─ error_reason (if failed)
    │     └─ timestamp
    │
    └─ Return result to client
       ├─ jobsFetched: 50
       ├─ jobsMatched: 15
       ├─ applicationsSubmitted: 14
       └─ applicationsFailed: 1

════════════════════════════════════════════════════════════════════
```

---

## Service Layer Details

### LinkedInJobFetcher
- **Input:** UserConfig (credentials, keywords), search params
- **Output:** List<Job> (extracted from LinkedIn)
- **Process:**
  1. Create Playwright browser instance
  2. Login with credentials
  3. Navigate to LinkedIn jobs search
  4. Filter by keywords, experience level
  5. Extract job details (title, company, salary, JD, application type)
  6. Return jobs

### JobMatcher
- **Input:** List<Job>, UserConfig (criteria)
- **Output:** Filtered List<Job>
- **Logic:**
  - Skip if already applied (check ApplicationRepository)
  - Skip if salary < minSalaryLPA
  - Skip if missing required keywords
  - Skip if contains blacklist keywords
  - Return matching jobs

### SchedulerService
- **Input:** userId, optional resumeId
- **Output:** Map with execution results
- **Orchestrates:**
  1. Load user config & resume
  2. Call LinkedInJobFetcher.searchJobs()
  3. Call JobMatcher.filterJobs()
  4. Save matched jobs
  5. [Phase 3] Call ApplicationSubmitter for each job
  6. Return summary

### ResumeTailor (Phase 2)
- **Input:** baseResume, job
- **Output:** tailored LaTeX, PDF, cover letter
- **Process:**
  1. Parse base LaTeX resume
  2. Send to Claude: "Select relevant projects and rewrite for this JD"
  3. Claude returns modified LaTeX
  4. Compile LaTeX to PDF with pdflatex
  5. Claude generates cover letter
  6. Return all artifacts

### ApplicationSubmitter (Phase 3)
- **Input:** job, tailoredResume (PDF), coverLetter
- **Output:** success/failure status
- **Process:**
  1. Detect application type (Easy Apply vs External)
  2. If Easy Apply: Playwright auto-fill + submit
  3. If External: Intelligent form detection + Claude assists
  4. Log result with error reason if failed

---

## Database Relationships

```
UserConfig (1) ←──┬──→ (Many) Resume
                  ├──→ (Many) Job
                  └──→ (Many) AuditLog

Job (1) ←──────────────→ (Many) Application

Resume (1) ←──────────────→ (Many) Application

Application
  - Stores reference to:
    - Job (which job was applied to)
    - Resume (which resume version was used)
    - Status, error reason, PDF path, cover letter
```

---

## Error Handling Strategy

| Error | Location | Handling |
|-------|----------|----------|
| LinkedIn login fails | LinkedInJobFetcher | Log error, return empty list |
| Job extraction fails | LinkedInJobFetcher | Skip that job, continue |
| Resume compilation fails | ResumeTailor | Log error, skip application |
| Form submission fails | ApplicationSubmitter | Log error, save attempt |
| Database error | Repository | Throw exception to controller |
| Invalid user config | SchedulerService | Return error response |

---

## Deployment Architecture

```
Production Server (Railway/Render)
├─ Java 17 runtime
├─ Spring Boot app (port 8080)
├─ PostgreSQL database (for production)
├─ TexLive (for LaTeX compilation)
├─ Chromium (for Playwright)
└─ Cron job (calls /api/scheduler/run every hour)
```

---

## Token Optimization

This architecture document is designed to give full context without loading code:
- Load this file for architectural questions
- Load COMPONENTS.md for specific service details
- Load DATABASE_SCHEMA.md for entity details
- Load individual Java files only when implementing
