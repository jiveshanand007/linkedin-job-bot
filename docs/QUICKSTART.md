# Quick Start — LinkedIn Job Apply Bot

End-to-end guide: from zero to automated applications in 5 steps.

---

## Prerequisites

| Tool | Install |
|---|---|
| Java 17+ | `sdk install java 17` |
| Maven | `brew install maven` |
| pdflatex | macOS: `brew install --cask mactex` / Linux: `sudo apt install texlive-latex-base` |
| Playwright browsers | `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"` |

---

## Step 1 — Start the Server

```bash
mvn spring-boot:run
# Server starts on http://localhost:8080
```

---

## Step 2 — Create Your User Config

```bash
curl -X POST http://localhost:8080/api/config/setup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "you@gmail.com",
    "linkedInEmail": "you@linkedin.com",
    "linkedInPasswordEncrypted": "your_linkedin_password",
    "jobKeywords": "Java,Spring,Backend",
    "blacklistKeywords": "Manager,Director,Lead",
    "minSalaryLPA": 20,
    "yearsExperienceMax": 3,
    "location": "Remote,Bangalore",
    "autoApplyEnabled": false,
    "phoneNumber": "9876543210"
  }'
# Response: { "id": 1, ... }
```

> **Tip:** Set `autoApplyEnabled: false` first to test without submitting real applications.

---

## Step 3 — Upload Your Base Resume (LaTeX)

```bash
curl -X POST http://localhost:8080/api/config/resumes/upload \
  -H "Content-Type: application/json" \
  -d '{
    "userConfigId": 1,
    "versionName": "v1",
    "latexContent": "\\documentclass{article}\\begin{document}...\\end{document}",
    "isActive": true
  }'
```

---

## Step 4 — (Optional) Configure Search Filters

```bash
curl -X POST http://localhost:8080/api/search-config \
  -H "Content-Type: application/json" \
  -d '{
    "userConfigId": 1,
    "remoteOnly": true,
    "experienceLevel": "ENTRY",
    "datePostedFilter": "PAST_WEEK",
    "maxPages": 3
  }'
```

| `experienceLevel` | LinkedIn filter |
|---|---|
| `ENTRY` | Entry level |
| `MID` | Mid-Senior level |
| `SENIOR` | Senior level |
| `DIRECTOR` | Director |
| `null` | Any level |

| `datePostedFilter` | LinkedIn filter |
|---|---|
| `PAST_DAY` | Last 24 hours |
| `PAST_WEEK` | Last 7 days |
| `PAST_MONTH` | Last 30 days |
| `ANY` | All time (default) |

---

## Step 5 — Run the Pipeline

### One-off manual run (recommended for first test)

```bash
curl -X POST "http://localhost:8080/api/scheduler/run?userId=1"
```

**Response:**
```json
{
  "status": "success",
  "jobsFetched": 47,
  "jobsMatched": 12,
  "applicationsSubmitted": 0,
  "tailoringErrors": 0
}
```

### Enable auto-apply (when ready)

```bash
# 1. Enable auto-apply on your config
curl -X PUT http://localhost:8080/api/config/update \
  -H "Content-Type: application/json" \
  -d '{ "id": 1, "autoApplyEnabled": true }'

# 2. Start the hourly scheduler
curl -X POST "http://localhost:8080/api/scheduler/start?userId=1"

# 3. Check scheduler status
curl "http://localhost:8080/api/scheduler/status?userId=1"
# { "schedulerActive": true, "autoApplyEnabled": true, "userId": 1 }

# 4. Stop when done
curl -X POST "http://localhost:8080/api/scheduler/stop?userId=1"
```

---

## Check Your Applications

```bash
curl http://localhost:8080/api/applications/user/1
```

```json
[
  {
    "id": 1,
    "jobId": 5,
    "jobTitle": "Senior Java Backend Developer",
    "company": "TechCorp",
    "status": "success",
    "submittedAt": "2026-05-06T21:00:00"
  },
  {
    "id": 2,
    "jobId": 6,
    "jobTitle": "Backend Engineer",
    "company": "StartupXYZ",
    "status": "failed",
    "errorReason": "Easy Apply form could not be completed"
  }
]
```

**Application statuses:**
| Status | Meaning |
|---|---|
| `pending` | Queued, in progress |
| `success` | Submitted to LinkedIn |
| `failed` | Error (see `errorReason`) |
| `skipped` | Not Easy Apply type |

---

## Full Pipeline Diagram

See [`docs/sequence-diagrams/end-to-end-pipeline.puml`](sequence-diagrams/end-to-end-pipeline.puml) for a PlantUML sequence diagram of the full flow.

Render it at [plantuml.com/plantuml](https://www.plantuml.com/plantuml) or with the PlantUML VS Code extension.

---

## Pipeline Overview (text)

```
POST /api/scheduler/run?userId=1
        │
        ▼
1. Load UserConfig from SQLite
        │
        ▼
2. LinkedIn Scrape (Playwright headless)
   └─ Login → Search → Extract job cards → Dedup
        │
        ▼
3. Filter Jobs (JobMatcher)
   └─ salary ≥ min │ required keywords │ no blacklist │ not already applied
        │
        ▼
4. Save matched jobs → SQLite
        │
        ▼
5. Tailor Resume per job (Claude API)
   └─ Base LaTeX + JD → Claude → Tailored LaTeX → Save to SQLite
        │
        ▼ (only if autoApplyEnabled = true)
6. Submit Applications (Playwright)
   └─ Compile PDF (pdflatex) → Login → Easy Apply → Upload PDF → Submit
        │
        ▼
7. Return { jobsFetched, jobsMatched, applicationsSubmitted, tailoringErrors }
```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `pdflatex: command not found` | Install texlive (see Prerequisites) |
| `LinkedIn login failed / challenge detected` | LinkedIn detected automation — wait, try again, or solve CAPTCHA manually once |
| `Easy Apply form could not be completed` | LinkedIn changed form selectors — check `PlaywrightApplicationSession` |
| `tailoringErrors > 0` | Check `CLAUDE_API_KEY` in `application.properties` |
| `jobsFetched: 0` | Verify LinkedIn credentials and search keywords |
