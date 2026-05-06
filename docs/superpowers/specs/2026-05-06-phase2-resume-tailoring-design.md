# Phase 2 Design: Resume Tailoring with Claude API

**Date:** 2026-05-06  
**Status:** Approved

---

## Problem

The scheduler pipeline (Phase 1) fetches and filters jobs but applies the same generic base resume to every job. Phase 2 adds automatic per-job resume tailoring: Claude selectively rewrites summary, skills, and experience bullet points in the LaTeX resume to match each job description. Tailored LaTeX is stored (no PDF compilation yet).

---

## Approach

Option A — `ResumeTailor` service embedded in `SchedulerService`. After `JobMatcher` filters jobs, `SchedulerService` calls `ResumeTailor.tailorAndSave()` for each matched job. Claude rewrites three sections of the LaTeX. Tailored LaTeX saved as a new `Resume` row linked to the job. On-demand endpoint also available.

---

## New Components

```
config/ClaudeApiConfig.java       ← reads CLAUDE_API_KEY, provides RestTemplate bean
service/ClaudeApiClient.java      ← raw HTTP client for Anthropic messages API
service/ResumeTailor.java         ← orchestrates Claude call + saves tailored Resume
controller/ResumeController.java  ← POST /api/resumes/tailor (on-demand)
```

## Modified Components

- `entity/Resume.java` — add `parentResumeId` (Long), `jobId` (Long)
- `service/SchedulerService.java` — add tailoring loop after saving matched jobs
- `application.properties` — add `claude.api.key`, `claude.model`, `claude.api.url`

---

## Data Flow

```
SchedulerService.executeRun(userId)
  → LinkedInJobFetcher.searchJobs()
  → JobMatcher.filterJobs()
  → jobRepository.saveAll(matchedJobs)
  → activeResume = resumeRepository.findFirstByUserConfigAndIsActive(config, true)
  → FOR EACH job in matchedJobs:
        resumeTailor.tailorAndSave(job, activeResume)
            → ClaudeApiClient.rewriteResume(latex, title, company, jd)
            → save new Resume { parentResumeId, jobId, versionName, isActive=false }
```

---

## Claude Prompt

```
You are a resume editor. Given a LaTeX resume and a job description,
rewrite ONLY these sections to better match the job:
1. Summary / objective section
2. Skills list (reorder to surface relevant skills first, do not add fake skills)
3. Experience bullet points (rephrase verbs, emphasize relevant tech)

Rules:
- Do NOT change dates, company names, job titles, or education
- Do NOT add experience or skills the candidate does not have
- Keep all LaTeX commands and formatting exactly intact
- Return ONLY the modified LaTeX, no explanation or markdown wrapper

JOB: {title} at {company}
JD: {jobDescription}

RESUME:
{latexContent}
```

---

## REST API

```
POST /api/resumes/tailor
Body: { "userId": 1, "jobId": 42 }
Response 200: { "resumeId": 99, "versionName": "tailored-for-42", "latexContent": "..." }
Response 400: user/resume/job not found
Response 500: Claude API failure
```

---

## Error Handling

- Claude failure → log, skip tailoring for that job, continue pipeline (non-fatal)
- No active base resume → log warning, skip tailoring for entire run
- Malformed Claude response → log, skip save
- Run result includes `tailoringErrors` count

---

## Out of Scope (Phase 2)

- PDF compilation (pdflatex)
- Cover letter generation
- LinkedIn Playwright automation
- Async/queued tailoring
- Caching tailored resumes
