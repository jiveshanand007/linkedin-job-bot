# REST API Reference - LinkedIn Job Apply Bot

## Base URL
```
http://localhost:8080
```

---

## Authentication
Currently **NO authentication** (Phase 1).  
Future: Add JWT token support in Phase 4+

---

## Configuration & Setup APIs

### 1. Create User Configuration

**Endpoint:** `POST /api/config/setup`

**Purpose:** Initialize bot for a user (required first step)

**Request:**
```bash
curl -X POST http://localhost:8080/api/config/setup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@gmail.com",
    "linkedInEmail": "user@linkedin.com",
    "linkedInPasswordEncrypted": "encrypted_password_here",
    "jobKeywords": "Java,Spring,Backend",
    "blacklistKeywords": "Manager,Director,Lead",
    "minSalaryLPA": 30,
    "yearsExperienceMax": 3,
    "location": "Remote,Bangalore"
  }'
```

**Response (Success - 200):**
```json
{
  "id": 1,
  "email": "user@gmail.com",
  "linkedInEmail": "user@linkedin.com",
  "jobKeywords": "Java,Spring,Backend",
  "blacklistKeywords": "Manager,Director,Lead",
  "minSalaryLPA": 30,
  "yearsExperienceMax": 3,
  "location": "Remote,Bangalore",
  "autoApplyEnabled": false,
  "createdAt": "2026-04-22T21:45:00",
  "updatedAt": "2026-04-22T21:45:00"
}
```

**Response (Error - 400):**
```json
{
  "error": "Email already exists"
}
```

**Fields:**
- `email` (required) - Your email address
- `linkedInEmail` (required) - LinkedIn login email
- `linkedInPasswordEncrypted` (required) - LinkedIn password (should be encrypted in production)
- `jobKeywords` (required) - CSV keywords to search (e.g., "Java,Spring,Backend")
- `blacklistKeywords` (optional) - CSV keywords to avoid (e.g., "Manager,Director")
- `minSalaryLPA` (required) - Minimum salary in Lakhs (30 = 30 LPA)
- `yearsExperienceMax` (required) - Max years of experience filter (0-3)
- `location` (required) - Job locations (CSV, e.g., "Remote,Bangalore")

**Returns:**
- `id` - User config ID (use for subsequent calls)
- All fields submitted + timestamps

---

### 2. Upload Resume

**Endpoint:** `POST /api/config/resumes/upload?userId=1`

**Purpose:** Upload base LaTeX resume for tailoring

**Request:**
```bash
curl -X POST "http://localhost:8080/api/config/resumes/upload?userId=1" \
  -H "Content-Type: application/json" \
  -d '{
    "versionName": "v1",
    "latexContent": "\\documentclass{article}\n\\begin{document}\n\\section{Experience}...\n\\end{document}"
  }'
```

**Response (Success - 200):**
```json
{
  "id": 1,
  "userConfig": {
    "id": 1
  },
  "versionName": "v1",
  "isActive": true,
  "uploadedAt": "2026-04-22T21:45:30",
  "updatedAt": "2026-04-22T21:45:30"
}
```

**Fields:**
- `versionName` (required) - Version identifier (e.g., "v1", "backend-focus", "data-engineer")
- `latexContent` (required) - Full LaTeX resume code

**Notes:**
- Store the returned `id` for future reference
- Use `versionName` to describe what each resume emphasizes
- Multiple resumes can be uploaded for different job types

---

### 3. List All Resumes

**Endpoint:** `GET /api/config/resumes/{userId}`

**Purpose:** View all uploaded resumes for a user

**Request:**
```bash
curl http://localhost:8080/api/config/resumes/1
```

**Response (Success - 200):**
```json
[
  {
    "id": 1,
    "versionName": "v1",
    "isActive": true,
    "uploadedAt": "2026-04-22T21:45:30",
    "updatedAt": "2026-04-22T21:45:30"
  },
  {
    "id": 2,
    "versionName": "v2-data-focus",
    "isActive": false,
    "uploadedAt": "2026-04-22T22:00:00",
    "updatedAt": "2026-04-22T22:00:00"
  }
]
```

**Notes:**
- Only metadata returned (not full LaTeX content to save bandwidth)
- `isActive` indicates if resume is in use

---

### 4. Get User Configuration

**Endpoint:** `GET /api/config/{userId}`

**Purpose:** Retrieve current configuration

**Request:**
```bash
curl http://localhost:8080/api/config/1
```

**Response (Success - 200):**
```json
{
  "id": 1,
  "email": "user@gmail.com",
  "linkedInEmail": "user@linkedin.com",
  "jobKeywords": "Java,Spring,Backend",
  "blacklistKeywords": "Manager,Director",
  "minSalaryLPA": 30,
  "yearsExperienceMax": 3,
  "location": "Remote",
  "autoApplyEnabled": false,
  "createdAt": "2026-04-22T21:45:00",
  "updatedAt": "2026-04-22T21:45:00"
}
```

---

## Job Search & Scheduling APIs

### 5. Manual Job Search Trigger

**Endpoint:** `POST /api/scheduler/run?userId=1`

**Purpose:** Manually trigger a job search and matching cycle

**Request:**
```bash
curl -X POST "http://localhost:8080/api/scheduler/run?userId=1" \
  -H "Content-Type: application/json"
```

**Response (Success - 200):**
```json
{
  "status": "success",
  "jobsFetched": 50,
  "jobsMatched": 15,
  "applicationsSubmitted": 0,
  "timestamp": "2026-04-22T21:45:45"
}
```

**Response (Error - 400):**
```json
{
  "status": "failed",
  "error": "User config not found"
}
```

**Fields in Response:**
- `status` - "success" or "failed"
- `jobsFetched` - Total jobs found on LinkedIn
- `jobsMatched` - Jobs matching your criteria
- `applicationsSubmitted` - Jobs applied to (Phase 3+)
- `timestamp` - When the run executed

**What Happens:**
1. LinkedInJobFetcher searches LinkedIn
2. JobMatcher filters by salary, keywords, blacklist
3. Matched jobs saved to database
4. (Future) Resumes generated and applications submitted

**Example Output Interpretation:**
- `jobsFetched: 100` - Found 100 job listings
- `jobsMatched: 8` - Only 8 met your criteria (salary, keywords, etc)
- `applicationsSubmitted: 0` - Phase 3 feature (not yet implemented)

---

### 6. Start Hourly Scheduler

**Endpoint:** `POST /api/scheduler/start?userId=1`

**Purpose:** Enable hourly automatic job searches (Phase 4)

**Request:**
```bash
curl -X POST "http://localhost:8080/api/scheduler/start?userId=1"
```

**Response (Phase 4 - 200):**
```json
{
  "status": "started",
  "message": "Hourly scheduler now active",
  "schedulerActive": true,
  "nextRun": "2026-04-22T22:00:00"
}
```

**Notes:**
- Phase 4: Uses @Scheduled(cron) with Spring scheduling
- Sets `scheduler_active=true` in user_config table
- Runs at top of every hour
- User can have multiple concurrent schedules

---

### 7. Stop Hourly Scheduler

**Endpoint:** `POST /api/scheduler/stop?userId=1`

**Purpose:** Disable hourly automatic job searches (Phase 4)

**Request:**
```bash
curl -X POST "http://localhost:8080/api/scheduler/stop?userId=1"
```

**Response (200):**
```json
{
  "status": "stopped",
  "schedulerActive": false
}
```

**Notes:**
- Phase 4: Disables @Scheduled job for user
- Sets `scheduler_active=false` in user_config table

---

### 8. Get Scheduler Status

**Endpoint:** `GET /api/scheduler/status?userId=1`

**Purpose:** Check if hourly scheduler is active for user (Phase 4)

**Request:**
```bash
curl "http://localhost:8080/api/scheduler/status?userId=1"
```

**Response (200):**
```json
{
  "schedulerActive": true,
  "autoApplyEnabled": false,
  "userId": 1,
  "nextRun": "2026-04-22T22:00:00",
  "lastRun": "2026-04-22T21:00:00"
}
```

**Response Fields:**
- `schedulerActive` - Is hourly scheduler currently enabled?
- `autoApplyEnabled` - Auto-apply enabled? (future feature)
- `userId` - User ID
- `nextRun` - Scheduled time of next run
- `lastRun` - Time of last run (null if never run)

**Notes:**
- Phase 4: Real-time status from database
- Shows next scheduled execution time

---

## Phase 3b APIs - Application Submission

### Get Application History

**Endpoint:** `GET /api/applications/user/{userId}`

**Purpose:** View all submitted applications for a user with status

**Request:**
```bash
curl "http://localhost:8080/api/applications/user/1?limit=20&offset=0&status=success"
```

**Response (Success - 200):**
```json
{
  "totalCount": 42,
  "applications": [
    {
      "id": 1,
      "jobId": 5,
      "jobTitle": "Senior Java Backend Developer",
      "company": "TechCorp",
      "status": "success",
      "submittedAt": "2026-04-22T21:46:00",
      "errorReason": null
    },
    {
      "id": 2,
      "jobId": 8,
      "jobTitle": "Backend Engineer",
      "company": "StartupXYZ",
      "status": "failed",
      "submittedAt": "2026-04-22T21:47:30",
      "errorReason": "Form submission timeout after 30s"
    }
  ]
}
```

**Query Parameters:**
- `limit` (optional, default: 20) - Number of records to return [1..100]
- `offset` (optional, default: 0) - Pagination offset
- `status` (optional) - Filter: "success", "failed", "pending"
- `orderBy` (optional, default: "submittedAt") - Sort field: "submittedAt", "createdAt", "status"

**Response (404 - Not Found):**
```json
{
  "error": "User not found: 1"
}
```

**Response (400 - Bad Request):**
```json
{
  "error": "Invalid limit: must be between 1 and 100"
}
```

**Response Fields:**
- `totalCount` - Total applications for user (ignoring limit/offset)
- `applications[]` - Array of application records
  - `id` - Application record ID
  - `jobId` - Linked job ID
  - `jobTitle` - Job title (denormalized for convenience)
  - `company` - Company name (denormalized)
  - `status` - "success", "failed", or "pending"
  - `submittedAt` - When application was submitted (null if pending)
  - `errorReason` - If status is "failed"

**Common Filters:**
```bash
# Get last 10 successful applications
curl "http://localhost:8080/api/applications/user/1?status=success&limit=10"

# Get failed applications for debugging
curl "http://localhost:8080/api/applications/user/1?status=failed&limit=50&orderBy=submittedAt"

# Get oldest pending applications (stuck jobs)
curl "http://localhost:8080/api/applications/user/1?status=pending&limit=10&orderBy=createdAt"
```

---

### Get Single Application Details

**Endpoint:** `GET /api/applications/{applicationId}`

**Purpose:** View detailed information about a specific application

**Request:**
```bash
curl "http://localhost:8080/api/applications/1"
```

**Response (Success - 200):**
```json
{
  "id": 1,
  "jobId": 5,
  "resumeId": 10,
  "job": {
    "id": 5,
    "title": "Senior Java Developer",
    "company": "TechCorp",
    "url": "https://linkedin.com/jobs/view/..."
  },
  "status": "success",
  "errorReason": null,
  "generatedPdfPath": "/pdfs/resume_abc123.pdf",
  "coverLetter": "Dear Hiring Manager,\n\nI am writing to express...",
  "resumeVersionHash": "sha256_hash_of_tailored_resume",
  "applicationResponse": {
    "message": "Your application has been submitted successfully",
    "confirmationId": "APP_12345"
  },
  "submittedAt": "2026-04-22T21:46:00",
  "createdAt": "2026-04-22T21:46:00"
}
```

**Response (404 - Not Found):**
```json
{
  "error": "Application not found: 1"
}
```

**Response Fields:**
- `id` - Application record ID
- `jobId` - Linked job ID
- `resumeId` - Tailored resume used
- `job` - Embedded job details
- `status` - "success", "failed", or "pending"
- `errorReason` - Full error text if failed
- `generatedPdfPath` - Path to compiled PDF resume
- `coverLetter` - Generated cover letter text
- `resumeVersionHash` - Hash of tailored resume for audit
- `applicationResponse` - JSON response from company form (if available)
- `submittedAt` - Submission timestamp
- `createdAt` - Record creation timestamp

---

## Phase 2 APIs

### Tailor Resume

**Endpoint:** `POST /api/resumes/tailor?resumeId=1&jobId=5`

**Purpose:** Tailor a specific resume for a specific job using Claude API. Stores the tailored LaTeX as a new resume version.

**Request:**
```bash
curl -X POST "http://localhost:8080/api/resumes/tailor?resumeId=1&jobId=5"
```

**Response (200):**
```json
{
  "resumeId": 99,
  "versionName": "tailored-for-5",
  "latexContent": "\\documentclass{article}..."
}
```

**Response (400):** `{ "error": "Resume not found: 1" }` or `{ "error": "Job not found: 5" }`  
**Response (500):** `{ "error": "Tailoring failed — check logs" }`

**Notes:**
- Uses the base resume specified by `resumeId`
- Tailoring also runs automatically during `POST /api/scheduler/run`
- Tailored resumes have `isActive=false` and `versionName="tailored-for-{jobId}"`

---

## Phase 3a APIs - Search Configuration

### Create Search Config

**Endpoint:** `POST /api/search-config`

**Purpose:** Create or initialize search filters for a user

**Request:**
```bash
curl -X POST http://localhost:8080/api/search-config \
  -H "Content-Type: application/json" \
  -d '{
    "userConfigId": 1,
    "remoteOnly": true,
    "experienceLevel": "MID",
    "datePostedFilter": "PAST_WEEK",
    "maxPages": 5
  }'
```

**Response (Success - 200):**
```json
{
  "id": 1,
  "userConfigId": 1,
  "remoteOnly": true,
  "experienceLevel": "MID",
  "datePostedFilter": "PAST_WEEK",
  "maxPages": 5,
  "createdAt": "2026-04-22T21:45:00",
  "updatedAt": "2026-04-22T21:45:00"
}
```

**Fields:**
- `userConfigId` (required) - User config ID
- `remoteOnly` (optional) - Filter to remote jobs only (default: false)
- `experienceLevel` (optional) - Filter by level: ENTRY | MID | SENIOR | DIRECTOR | null (any)
- `datePostedFilter` (optional) - Filter by posting date: PAST_DAY | PAST_WEEK | PAST_MONTH | ANY (default: ANY)
- `maxPages` (optional) - Maximum pages to fetch [1..10] (default: 3)

---

### Update Search Config

**Endpoint:** `PUT /api/search-config/{id}`

**Purpose:** Full-replace update of search configuration (null values reset to defaults)

**Request:**
```bash
curl -X PUT http://localhost:8080/api/search-config/1 \
  -H "Content-Type: application/json" \
  -d '{
    "remoteOnly": false,
    "experienceLevel": "SENIOR",
    "datePostedFilter": "PAST_MONTH",
    "maxPages": 3
  }'
```

**Response (Success - 200):**
```json
{
  "id": 1,
  "userConfigId": 1,
  "remoteOnly": false,
  "experienceLevel": "SENIOR",
  "datePostedFilter": "PAST_MONTH",
  "maxPages": 3,
  "updatedAt": "2026-04-22T22:00:00"
}
```

**Notes:**
- Any null fields are reset to defaults
- userConfigId cannot be changed

---

### Get Search Config by User

**Endpoint:** `GET /api/search-config/user/{userId}`

**Purpose:** Retrieve search config for a specific user

**Request:**
```bash
curl http://localhost:8080/api/search-config/user/1
```

**Response (Success - 200):**
```json
{
  "id": 1,
  "userConfigId": 1,
  "remoteOnly": true,
  "experienceLevel": "MID",
  "datePostedFilter": "PAST_WEEK",
  "maxPages": 5,
  "createdAt": "2026-04-22T21:45:00",
  "updatedAt": "2026-04-22T21:45:00"
}
```

**Response (Not Found - 404):**
```json
{
  "error": "Search config not found for user: 1"
}
```

---

### Delete Search Config

**Endpoint:** `DELETE /api/search-config/{id}`

**Purpose:** Delete a search configuration

**Request:**
```bash
curl -X DELETE http://localhost:8080/api/search-config/1
```

**Response (Success - 204):** No content

**Response (Not Found - 404):**
```json
{
  "error": "Search config not found: 1"
}
```

---

### Get Application History (Phase 4)
```
GET /api/logs?userId=1&limit=100&action=application_submitted
  → Returns complete audit trail with filtering by action type
```

---

## Error Codes

| Code | Meaning | Example |
|------|---------|---------|
| 200 | Success | Job search completed |
| 400 | Bad Request | User not found, invalid input |
| 404 | Not Found | Resume ID doesn't exist |
| 500 | Server Error | Database connection failed |

---

## Usage Workflow

### Step 1: Setup (One-time)
```bash
# Create configuration
curl -X POST http://localhost:8080/api/config/setup \
  -H "Content-Type: application/json" \
  -d '{ ... user config ... }'

# Save the returned userId (let's say it's 1)

# Upload resume
curl -X POST "http://localhost:8080/api/config/resumes/upload?userId=1" \
  -H "Content-Type: application/json" \
  -d '{ "versionName": "v1", "latexContent": "..." }'
```

### Step 2: Trigger Job Search
```bash
# Manually trigger a search
curl -X POST "http://localhost:8080/api/scheduler/run?userId=1"

# Response shows:
# - 50 jobs found
# - 15 matching your criteria
# (In Phase 3, these will be applied to automatically)
```

### Step 3: Monitor (Phase 4)
```bash
# View all applications
curl "http://localhost:8080/api/applications/history?userId=1"

# View audit log
curl "http://localhost:8080/api/logs?userId=1"
```

---

## Rate Limiting

Currently: **NONE** (add in Phase 4)

Future considerations:
- LinkedIn: Delay between requests (avoid bot detection)
- API: Rate limit by user (e.g., 1 request/minute)

---

## CORS

Currently enabled for all origins (`*`).

Production: Change to specific frontend domain:
```java
@CrossOrigin(origins = "https://yourfrontend.com")
```
