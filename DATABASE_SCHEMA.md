# Database Schema - LinkedIn Job Apply Bot

## Overview
5 JPA entities stored in SQLite, with relationships defining the data model.

---

## Entity Relationship Diagram

```
UserConfig (1) ──┬──→ (1)    SearchConfig
                 ├──→ (Many) Resume
                 ├──→ (Many) Job
                 └──→ (Many) AuditLog

Job (1) ────────→ (Many) Application

Resume (1) ─────→ (Many) Application

Application
  ├─ job_id (FK → Job)
  ├─ resume_id (FK → Resume)
  └─ status, error_reason, generatedPdfPath, etc

SearchConfig
  └─ user_config_id (FK → UserConfig, UNIQUE)
```

---

## Table: user_config

Stores user credentials and job search preferences.

**Columns:**

| Column | Type | Null | Notes |
|--------|------|------|-------|
| id | BIGINT | NO | Primary Key, Auto-increment |
| email | VARCHAR(255) | NO | Unique, user's email |
| linkedin_email | VARCHAR(255) | NO | LinkedIn login email |
| linkedin_password_encrypted | VARCHAR(255) | NO | Encrypted password |
| job_keywords | TEXT | YES | CSV: "Java,Spring,Backend" |
| blacklist_keywords | TEXT | YES | CSV: "Manager,Director" |
| min_salary_lpa | INT | NO | Minimum salary in LPA |
| years_experience_max | INT | NO | Max years filter (0-3) |
| location | VARCHAR(255) | NO | "Remote,Bangalore" |
| auto_apply_enabled | BOOLEAN | NO | Default: false |
| created_at | TIMESTAMP | YES | Insertion timestamp |
| updated_at | TIMESTAMP | YES | Last update timestamp |

**Example Row:**
```
id: 1
email: user@gmail.com
linkedin_email: user@linkedin.com
linkedin_password_encrypted: [encrypted]
job_keywords: Java,Spring,Backend
blacklist_keywords: Manager,Director
min_salary_lpa: 30
years_experience_max: 3
location: Remote
auto_apply_enabled: false
created_at: 2026-04-22 21:45:00
updated_at: 2026-04-22 21:45:00
```

---

## Table: search_config

Stores per-user job search filter preferences (Phase 3a).

**Columns:**

| Column | Type | Null | Notes |
|--------|------|------|-------|
| id | BIGINT | NO | Primary Key, Auto-increment |
| user_config_id | BIGINT | NO | Unique Foreign Key → user_config.id |
| remote_only | BOOLEAN | NO | Default: false (filter to remote jobs only) |
| experience_level | TEXT | YES | ENTRY \| MID \| SENIOR \| DIRECTOR \| NULL = any |
| date_posted_filter | TEXT | NO | Default: 'ANY' — PAST_DAY \| PAST_WEEK \| PAST_MONTH \| ANY |
| max_pages | INTEGER | NO | Default: 3 — Max pages to fetch [1..10] |
| created_at | TIMESTAMP | YES | Record creation time |
| updated_at | TIMESTAMP | YES | Last update timestamp |

**Constraints:**
- Foreign Key: `user_config_id` → `user_config.id` (UNIQUE - one config per user)

**Example Row:**
```
id: 1
user_config_id: 1
remote_only: true
experience_level: MID
date_posted_filter: PAST_WEEK
max_pages: 5
created_at: 2026-04-22 21:45:00
updated_at: 2026-04-22 21:45:00
```

**Notes:**
- One search_config per user (UNIQUE constraint on user_config_id)
- Replaces LinkedInJobFetcher inline config logic with persistent storage
- Used by LinkedInJobFetcher.searchJobs() to apply filters

---

## Table: resumes

Stores LaTeX resumes for each user.

**Columns:**

| Column | Type | Null | Notes |
|--------|------|------|-------|
| id | BIGINT | NO | Primary Key |
| user_config_id | BIGINT | NO | Foreign Key → user_config |
| version_name | VARCHAR(255) | NO | "v1", "backend-focus", etc |
| latex_content | LONGTEXT | NO | Full LaTeX resume code |
| is_active | BOOLEAN | NO | Currently in use? |
| parent_resume_id | BIGINT | YES | FK → resumes.id — null for base resumes, set for tailored versions |
| job_id           | BIGINT | YES | FK → jobs.id — which job this resume was tailored for |
| uploaded_at | TIMESTAMP | YES | When uploaded |
| updated_at | TIMESTAMP | YES | Last modification |

**Constraints:**
- Foreign Key: `user_config_id` → `user_config.id`

**Example Row:**
```
id: 1
user_config_id: 1
version_name: v1
latex_content: \documentclass{article}...
is_active: true
uploaded_at: 2026-04-22 21:45:30
updated_at: 2026-04-22 21:45:30
```

**Notes:**
- Multiple resumes per user allowed
- Each resume can be tailored for different job types
- `is_active` tracks which version is currently used

---

## Table: jobs

Stores job listings extracted from LinkedIn.

**Columns:**

| Column | Type | Null | Notes |
|--------|------|------|-------|
| id | BIGINT | NO | Primary Key |
| user_config_id | BIGINT | NO | Foreign Key → user_config |
| linkedin_job_id | VARCHAR(255) | NO | LinkedIn job ID (unique per user) |
| title | VARCHAR(255) | NO | Job title |
| company | VARCHAR(255) | NO | Company name |
| job_description | LONGTEXT | YES | Full JD text |
| salary | INT | YES | Salary in LPA |
| application_type | VARCHAR(50) | YES | "easy_apply" or "external" |
| url | VARCHAR(2000) | YES | LinkedIn job URL |
| location | VARCHAR(255) | YES | Job location |
| extracted_at | TIMESTAMP | YES | When job was found |

**Constraints:**
- Foreign Key: `user_config_id` → `user_config.id`
- Unique Constraint: (`user_config_id`, `linkedin_job_id`) - Don't store duplicate jobs

**Example Row:**
```
id: 1
user_config_id: 1
linkedin_job_id: 3847293847
title: Senior Java Backend Developer
company: TechCorp
job_description: We are looking for...
salary: 35
application_type: easy_apply
url: https://linkedin.com/jobs/view/...
location: Bangalore, India
extracted_at: 2026-04-22 21:45:45
```

**Notes:**
- Multiple jobs per user
- `salary` can be NULL if not listed
- `application_type` determines submission method (Phase 3)

---

## Table: applications

Tracks every application attempt.

**Columns:**

| Column | Type | Null | Notes |
|--------|------|------|-------|
| id | BIGINT | NO | Primary Key |
| job_id | BIGINT | NO | Foreign Key → jobs |
| resume_id | BIGINT | YES | Foreign Key → resumes (which resume version used) |
| status | VARCHAR(50) | NO | "success", "failed", "pending" |
| error_reason | LONGTEXT | YES | Why application failed |
| generated_pdf_path | VARCHAR(2000) | YES | Path to generated PDF |
| cover_letter | LONGTEXT | YES | Generated cover letter text |
| resume_version_hash | VARCHAR(255) | YES | Hash of tailored resume (for audit) |
| application_response | LONGTEXT | YES | JSON response from company form |
| submitted_at | TIMESTAMP | YES | When application was submitted |
| created_at | TIMESTAMP | YES | Record creation time |

**Constraints:**
- Foreign Key: `job_id` → `jobs.id`
- Foreign Key: `resume_id` → `resumes.id` (nullable)

**Example Row (Success):**
```
id: 1
job_id: 1
resume_id: 1
status: success
error_reason: NULL
generated_pdf_path: /pdfs/resume_abc123.pdf
cover_letter: Dear Hiring Manager...
resume_version_hash: sha256_hash_of_tailored_resume
application_response: {"status": "Application submitted"}
submitted_at: 2026-04-22 21:46:00
created_at: 2026-04-22 21:46:00
```

**Example Row (Failed):**
```
id: 2
job_id: 2
resume_id: 1
status: failed
error_reason: Form submission failed: Could not detect email field
generated_pdf_path: /pdfs/resume_def456.pdf
cover_letter: NULL
resume_version_hash: sha256_hash
application_response: NULL
submitted_at: NULL
created_at: 2026-04-22 21:46:30
```

**Statuses:**
- `pending` - Queued, not yet attempted
- `success` - Application submitted successfully
- `failed` - Submission failed (see `error_reason`)

---

## Table: audit_logs

Complete audit trail of all actions.

**Columns:**

| Column | Type | Null | Notes |
|--------|------|------|-------|
| id | BIGINT | NO | Primary Key |
| user_config_id | BIGINT | NO | Foreign Key → user_config |
| action | VARCHAR(100) | NO | What happened |
| details | LONGTEXT | YES | JSON with action details |
| timestamp | TIMESTAMP | YES | When action occurred |

**Constraints:**
- Foreign Key: `user_config_id` → `user_config.id`
- Index on `timestamp` for fast sorting

**Example Rows:**

```
id: 1
user_config_id: 1
action: job_matched
details: {"jobId": 1, "title": "Java Developer", "company": "TechCorp"}
timestamp: 2026-04-22 21:45:45

id: 2
user_config_id: 1
action: resume_generated
details: {"jobId": 1, "resumeId": 1, "pdfPath": "..."}
timestamp: 2026-04-22 21:46:00

id: 3
user_config_id: 1
action: application_submitted
details: {"jobId": 1, "status": "success"}
timestamp: 2026-04-22 21:46:05

id: 4
user_config_id: 1
action: application_failed
details: {"jobId": 2, "error": "Form submission timeout"}
timestamp: 2026-04-22 21:46:30
```

**Common Actions:**
- `job_matched` - Job passed filtering
- `resume_generated` - Tailored resume created
- `application_submitted` - Application sent
- `application_failed` - Application failed
- `scheduler_run_started` - Bot cycle started
- `scheduler_run_completed` - Bot cycle finished

---

## SQL Queries (Examples)

### Find all jobs for a user
```sql
SELECT * FROM jobs 
WHERE user_config_id = 1 
ORDER BY extracted_at DESC;
```

### Count successful applications
```sql
SELECT COUNT(*) as count 
FROM applications 
WHERE user_config_id = 1 AND status = 'success';
```

### Find failed applications with reasons
```sql
SELECT j.title, j.company, a.error_reason, a.submitted_at
FROM applications a
JOIN jobs j ON a.job_id = j.id
WHERE a.status = 'failed' AND a.user_config_id = 1
ORDER BY a.submitted_at DESC;
```

### Get audit trail for specific job
```sql
SELECT * FROM audit_logs
WHERE user_config_id = 1
ORDER BY timestamp DESC
LIMIT 100;
```

### Check if job already applied
```sql
SELECT * FROM applications
WHERE job_id = 1 AND status = 'success';
```

### Get active resumes for user
```sql
SELECT * FROM resumes
WHERE user_config_id = 1 AND is_active = true;
```

---

## Schema Evolution (Versions)

### V1 (Current)
- 5 core tables
- Basic relationships
- Audit logging

### V2 (Future - Phase 4+)
- Add `scheduled_runs` table (track hourly runs)
- Add `user_notes` field to `applications`
- Add `follow_up_date` to `applications`
- Add indexes for performance

### V3 (Future - Scaling)
- Partition jobs/applications by date
- Add caching layer
- Add `interview_status` field

---

## Indexes (Phase 1)

Current indexes (automatic via Primary Keys and Unique constraints):
- `user_config.id` (PK)
- `jobs.id` (PK)
- `jobs.user_config_id + linkedin_job_id` (Unique)
- `applications.id` (PK)
- `resumes.id` (PK)
- `audit_logs.id` (PK)

### Recommended Indexes (Phase 4+)
```sql
CREATE INDEX idx_jobs_user ON jobs(user_config_id);
CREATE INDEX idx_applications_job ON applications(job_id);
CREATE INDEX idx_audit_user_timestamp ON audit_logs(user_config_id, timestamp DESC);
CREATE INDEX idx_jobs_extracted ON jobs(extracted_at DESC);
```

---

## Data Retention Policy

**Recommended (implement in Phase 5):**
- Keep jobs for 90 days
- Keep applications for 1 year
- Keep audit logs for 1 year
- Keep resumes indefinitely

```sql
-- Archive old jobs (quarterly)
DELETE FROM jobs 
WHERE extracted_at < DATE('now', '-90 days');
```

---

## Database Migrations

### Phase 1 → Phase 2
- Add `generated_pdf_path` to applications (already included)
- No schema changes

### Phase 2 → Phase 3
- Add `cover_letter` to applications (already included)
- No schema changes

### Phase 3 → Phase 4
- Add `scheduled_runs` table
- Add indexes for performance

---

## Summary

| Table | Rows/User | Purpose | Indexed |
|-------|-----------|---------|---------|
| user_config | 1 | Credentials & preferences | Yes (PK) |
| resumes | 2-5 | LaTeX resume versions | Yes (PK, FK) |
| jobs | 10-50 per run | Job listings from LinkedIn | Yes (Unique) |
| applications | 5-20 per run | Application records | Yes (PK, FK) |
| audit_logs | 20-100 per run | Complete audit trail | Yes (PK, FK) |
