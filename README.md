# LinkedIn Job Apply Bot - Project Summary

## Overview

**Fully automated LinkedIn job application bot** that:
1. Searches LinkedIn for jobs matching your criteria (keywords, salary, experience level)
2. Generates AI-tailored resumes & cover letters using Claude API
3. Applies to jobs automatically with tailored documents
4. Tracks all applications with audit logs

**Status:** Phase 1 ✅ Complete | Phase 2 ✅ Complete | Phase 3a ✅ Complete | Phase 3b+ ⏳  
**Tech Stack:** Java 17, Spring Boot 3.2, SQLite, Playwright, Claude API  
**Codebase:** 20 Java files

---

## Project Directory Structure

```
linkedin-job-bot/
├── pom.xml                          # Maven dependencies & build config
├── README.md                        # This file — start here
├── ARCHITECTURE.md                  # System design & data flow
├── COMPONENTS.md                    # Detailed component descriptions
├── API_ENDPOINTS.md                 # REST API reference
├── DATABASE_SCHEMA.md               # Entity relationships & schema
├── docs/superpowers/
│   ├── specs/                       # Design specs per phase
│   └── plans/                       # Implementation plans per phase
├── src/main/
│   ├── java/com/jobbot/
│   │   ├── LinkedInJobBotApplication.java
│   │   ├── config/
│   │   │   └── ClaudeApiConfig.java        # Claude API + RestTemplate bean
│   │   ├── controller/
│   │   │   ├── ConfigController.java       # Setup & resume APIs
│   │   │   ├── SchedulerController.java    # Job search & scheduling APIs
│   │   │   └── ResumeController.java       # Resume tailor API
│   │   ├── service/
│   │   │   ├── LinkedInJobFetcher.java     # LinkedIn automation (stub → Phase 3)
│   │   │   ├── JobMatcher.java             # Filter jobs by criteria
│   │   │   ├── SchedulerService.java       # Orchestrate pipeline
│   │   │   ├── ClaudeApiClient.java        # Anthropic API HTTP client
│   │   │   └── ResumeTailor.java           # AI resume tailoring
│   │   ├── entity/                  # JPA entities (5 classes)
│   │   └── repository/              # Data access layer (5 interfaces)
│   └── resources/
│       └── application.properties
└── src/test/                        # Tests (Phase 5)

```

---

## Quick Reference: What Each Component Does

| Component | Files | Purpose |
|-----------|-------|---------|
| **Entities** | 6 Java files | Database models: UserConfig, Resume, Job, Application, AuditLog, SearchConfig |
| **Repositories** | 6 interfaces | JPA data access for all entities |
| **LinkedInJobFetcher** | 1 service | Searches LinkedIn using Playwright (Phase 3a Complete) |
| **PlaywrightSessionManager** | 1 helper | Browser lifecycle & LinkedIn login (Phase 3a) |
| **JobParser** | 1 service | DOM-to-Job mapping (Phase 3a) |
| **JobMatcher** | 1 service | Filters jobs by keywords, salary, blacklist |
| **SchedulerService** | 1 service | Orchestrates the full job search→match→apply pipeline |
| **ConfigController** | 1 controller | APIs for setup, resume upload, configuration |
| **SchedulerController** | 1 controller | APIs for triggering job searches and scheduling |
| **SearchConfigController** | 1 controller | REST CRUD for search filters (Phase 3a) |

---

## Current Status by Phase

### ✅ Phase 1: Core Backend (COMPLETE)
- Database schema with 5 JPA entities
- JobMatcher service (filters jobs)
- REST APIs for configuration & manual triggers

### ✅ Phase 2: Resume Tailoring (COMPLETE)
- `ClaudeApiConfig` — RestTemplate bean + API key config
- `ClaudeApiClient` — raw HTTP client for Anthropic messages API
- `ResumeTailor` — tailors LaTeX per job, stores result as new Resume row
- `ResumeController` — `POST /api/resumes/tailor?resumeId=1&jobId=5`
- `SchedulerService` updated — auto-tailors for each matched job per run

### ✅ Phase 3a: LinkedIn Job Fetching with Playwright (COMPLETE)
- `PlaywrightSessionManager` — Browser lifecycle and LinkedIn login
- `LinkedInJobFetcher` — Real Playwright automation with SearchConfig filters
- `JobParser` — DOM extraction and salary normalization
- `JobCardData` — Intermediate data structure
- `SearchConfig` — JPA entity for per-user search preferences
- `SearchConfigController` — REST CRUD endpoints for search filters

### ⏳ Phase 3b: Application Submission (TODO)
- ApplicationSubmitter — Easy Apply + external form filling

### ⏳ Phase 4: Full Automation (TODO)
- Quartz scheduler (hourly runs)
- Application history & audit log endpoints

### ⏳ Phase 5: Testing & Deployment (TODO)

---

## How Data Flows Through the System

```
User uploads resume & config
         ↓
[ConfigController] saves to UserConfig & Resume tables
         ↓
User triggers job search (POST /api/scheduler/run)
         ↓
[SchedulerService] calls LinkedInJobFetcher.searchJobs()
         ↓
[LinkedInJobFetcher] uses Playwright to:
  - Login to LinkedIn
  - Search jobs matching criteria
  - Extract job details
  - Return Job list
         ↓
[SchedulerService] passes jobs to JobMatcher.filterJobs()
         ↓
[JobMatcher] filters by:
  - Salary >= minSalaryLPA
  - Contains required keywords
  - Not in blacklist
  - Not already applied
         ↓
[SchedulerService] saves matched jobs to database
         ↓
Future Phase 3: Apply to each job with tailored resume
         ↓
[AuditLogger] tracks all actions
```

---

## Database Schema (5 Entities)

| Table | Key Fields | Purpose |
|-------|-----------|---------|
| **user_config** | id, email, linkedInEmail, jobKeywords, minSalaryLPA | User credentials & preferences |
| **resumes** | id, user_id, versionName, latexContent, isActive | Store base LaTeX resumes |
| **jobs** | id, user_id, linkedInJobId, title, company, salary, jd | Job listings from LinkedIn |
| **applications** | id, job_id, resume_id, status, generatedPdfPath, errorReason | Application attempts & results |
| **audit_logs** | id, user_id, action, details, timestamp | Complete audit trail |

See `DATABASE_SCHEMA.md` for full details.

---

## REST API Endpoints (Phase 1)

### Configuration & Setup
```
POST   /api/config/setup                    → Create user config
POST   /api/config/resumes/upload?userId=X  → Upload base resume
GET    /api/config/resumes/{userId}         → List all resumes
GET    /api/config/{userId}                 → Get user config
```

### Job Search & Scheduling
```
POST   /api/scheduler/run?userId=X          → Manually trigger job search
POST   /api/scheduler/start?userId=X        → Start hourly scheduler (Phase 4)
POST   /api/scheduler/stop?userId=X         → Stop hourly scheduler (Phase 4)
```

See `API_ENDPOINTS.md` for full reference with examples.

---

## Key Files to Know

### For Understanding Architecture
- `ARCHITECTURE.md` - System design, data flow diagrams
- `COMPONENTS.md` - Detailed description of each service

### For Understanding Data
- `DATABASE_SCHEMA.md` - Entity relationships and fields
- `src/main/java/com/jobbot/entity/` - Entity class definitions

### For API Integration
- `API_ENDPOINTS.md` - All endpoints with curl examples
- `src/main/java/com/jobbot/controller/` - Controller implementations

### Configuration & Setup
- `src/main/resources/application.properties` - Spring Boot config
- `pom.xml` - Maven dependencies

---

## Environment Variables & Secrets

Required (before running):
```bash
export CLAUDE_API_KEY=sk-ant-xxxxx
```

Optional (configured in application.properties):
```bash
server.port=8080
spring.datasource.url=jdbc:sqlite:jobbot.db
```

---

## Dependencies Summary

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Spring Boot | 3.2.0 | Web framework & auto-config |
| Spring Data JPA | - | Database ORM |
| SQLite | 3.44.0 | Database |
| Hibernate (Community Dialect) | 6.4.0 | SQLite dialect |
| Playwright | 1.40.0 | Browser automation |
| Quartz | - | Job scheduling |
| Jackson | - | JSON serialization |

---

## Next Steps for Phase 2

1. Install TexLive on the server (for LaTeX compilation)
2. Create `ClaudeApiConfig.java` for API client setup
3. Create `ResumeTailor.java` service for Claude integration
4. Create `LaTeXCompiler.java` utility for PDF generation
5. Add `ResumeController.java` for tailoring endpoint
6. Write tests in Phase 5

See `plan.md` for detailed implementation plan.

---

## How to Use This Project

### Install Playwright browser binaries (one-time setup)
```bash
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

### For Development
1. Read `ARCHITECTURE.md` to understand the design
2. Read `COMPONENTS.md` for detailed component descriptions
3. Check `API_ENDPOINTS.md` for testing endpoints
4. Review entity classes in `entity/` folder

### For Running Locally
1. Set `CLAUDE_API_KEY` environment variable
2. Run `mvn clean install` to download dependencies
3. Run `mvn spring-boot:run` to start server (port 8080)
4. Use curl or Postman to call `/api/config/setup` first

### For Continuing Development
- Phase 1-3a complete — begin Phase 3b (ApplicationSubmitter)
- Each phase builds on the previous one
- Tests will be added in Phase 5
- Refer to `plan.md` for detailed task breakdown

---

## Token Optimization Notes

This documentation is designed to minimize token usage in future sessions:
- **ARCHITECTURE.md** - High-level overview (load for design questions)
- **COMPONENTS.md** - Component details (load to understand specific service)
- **API_ENDPOINTS.md** - API reference (load for API integration)
- **DATABASE_SCHEMA.md** - Entity details (load for database questions)
- Main code files only loaded when implementation is needed

Load only the documentation files relevant to your question to save tokens.

---

## Contact & Support

This project was generated by GitHub Copilot CLI.  
All code follows standard Spring Boot conventions and best practices.
