# LinkedIn Job Apply Bot - Project Summary

## Overview

**Fully automated LinkedIn job application bot** that:
1. Searches LinkedIn for jobs matching your criteria (keywords, salary, experience level)
2. Generates AI-tailored resumes & cover letters using Claude API
3. Applies to jobs automatically with tailored documents
4. Tracks all applications with audit logs

**Status:** Phase 1 Complete (Backend core setup)  
**Tech Stack:** Java 17, Spring Boot 3.2, SQLite, Playwright, Claude API  
**Codebase:** 16 Java files, 663 lines  

---

## Project Directory Structure

```
linkedin-job-bot/
├── pom.xml                          # Maven dependencies & build config
├── README.md                        # This file
├── ARCHITECTURE.md                  # System design & data flow
├── COMPONENTS.md                    # Detailed component descriptions
├── API_ENDPOINTS.md                 # REST API reference
├── DATABASE_SCHEMA.md               # Entity relationships
├── PHASE1_COMPLETE.md               # Phase 1 completion details
├── src/main/
│   ├── java/com/jobbot/
│   │   ├── LinkedInJobBotApplication.java  # Entry point
│   │   ├── config/                  # Spring configurations (empty - Phase 2+)
│   │   ├── controller/
│   │   │   ├── ConfigController.java       # Setup & resume APIs
│   │   │   └── SchedulerController.java    # Job search & scheduling APIs
│   │   ├── service/
│   │   │   ├── LinkedInJobFetcher.java     # LinkedIn automation (Playwright)
│   │   │   ├── JobMatcher.java             # Filter jobs by criteria
│   │   │   └── SchedulerService.java       # Orchestrate pipeline
│   │   ├── entity/                  # JPA entities (5 classes)
│   │   │   ├── UserConfig.java
│   │   │   ├── Resume.java
│   │   │   ├── Job.java
│   │   │   ├── Application.java
│   │   │   └── AuditLog.java
│   │   └── repository/              # Data access layer (5 interfaces)
│   └── resources/
│       └── application.properties    # Spring Boot configuration
├── src/test/                        # Tests (Phase 5)
└── .git/                            # Git repository

```

---

## Quick Reference: What Each Component Does

| Component | Files | Purpose |
|-----------|-------|---------|
| **Entities** | 5 Java files | Database models: UserConfig, Resume, Job, Application, AuditLog |
| **Repositories** | 5 interfaces | JPA data access for all entities |
| **LinkedInJobFetcher** | 1 service | Searches LinkedIn using Playwright (placeholder - Phase 2) |
| **JobMatcher** | 1 service | Filters jobs by keywords, salary, blacklist |
| **SchedulerService** | 1 service | Orchestrates the full job search→match→apply pipeline |
| **ConfigController** | 1 controller | APIs for setup, resume upload, configuration |
| **SchedulerController** | 1 controller | APIs for triggering job searches and scheduling |

---

## Current Status by Phase

### ✅ Phase 1: Core Backend (COMPLETE)
- Database schema with 5 JPA entities
- JobMatcher service (filters jobs)
- REST APIs for configuration & manual triggers
- Basic skeleton in place for future phases

**What works:**
- User configuration setup
- Resume upload/storage
- Job filtering logic
- Database persistence

**What's NOT implemented yet:**
- LinkedIn automation (Playwright)
- Resume tailoring (Claude API)
- Application submission
- Hourly scheduling

### ⏳ Phase 2: Resume Tailoring (TODO)
- Claude API integration
- LaTeX parsing & modification
- pdflatex compilation to PDF
- Resume tailoring endpoint

### ⏳ Phase 3: Application Submission (TODO)
- Easy Apply automation
- External form filling
- Claude-assisted field detection

### ⏳ Phase 4: Full Automation (TODO)
- Quartz scheduler setup
- Hourly job runs
- End-to-end pipeline

### ⏳ Phase 5: Testing & Deployment (TODO)
- Unit tests
- Integration tests
- Production deployment

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
