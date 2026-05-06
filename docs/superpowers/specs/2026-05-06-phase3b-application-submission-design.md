# Phase 3b — Application Submission Design

**Date:** 2026-05-06  
**Status:** Draft  
**Phase:** 3b (of 5)

---

## Problem Statement

After Phase 3a, the pipeline fetches + filters + tailors resumes but submits zero applications (`applicationsSubmitted: 0` is hardcoded). Phase 3b closes the loop: compile the tailored LaTeX resume to PDF, then use Playwright to submit LinkedIn Easy Apply applications.

---

## Scope

- **In:** LinkedIn Easy Apply jobs only (`applicationType == "easy_apply"`)
- **In:** Only when `UserConfig.autoApplyEnabled == true`
- **In:** LaTeX → PDF compilation via `pdflatex`
- **In:** PDF upload during Easy Apply
- **In:** Cover letter field population (from `Application.coverLetter` if present)
- **In:** `Application` entity saved for every attempt (success or failure)
- **Out:** External job portals (company websites) — Phase 5 / future
- **Out:** Multi-round forms with custom questions — skip and mark as `skipped`

---

## Architecture

| Class | Responsibility | Interface |
|---|---|---|
| `LaTeXCompiler` | `@Component`. Compiles LaTeX string to PDF on disk using system `pdflatex`. Returns absolute PDF path. Throws `LaTeXCompilationException` on failure. | `String compileToPdf(String latexContent, Long jobId)` |
| `ApplicationSubmitter` | `@Component`. Orchestrates: compile PDF → log in → Easy Apply flow → save `Application`. Returns saved `Application`. | `Application submit(Job job, Resume tailoredResume, UserConfig config)` |
| `LaTeXCompilationException` | Unchecked exception thrown when `pdflatex` exits non-zero or times out. | — |
| `ApplicationController` | `@RestController`. Read-only endpoint to list applications per user. | `GET /api/applications/user/{userId}` |
| `ApplicationRepository` | Already exists — no new methods needed beyond what's there. | — |

**SchedulerService integration (two changes):**
```
// After tailoring loop, add:
int applicationsSubmitted = 0;
if (config.isAutoApplyEnabled()) {
    for (Job job : matchedJobs) {
        // find tailored resume for this job
        Optional<Resume> tailored = resumeRepository.findFirstByJobAndUserConfig(job, config);
        if (tailored.isPresent()) {
            try {
                applicationSubmitter.submit(job, tailored.get(), config);
                applicationsSubmitted++;
            } catch (Exception e) {
                logger.error("Application failed for job {}", job.getId(), e);
            }
        }
    }
}
result.put("applicationsSubmitted", applicationsSubmitted);
```

---

## LaTeXCompiler — Detail

```
compileToPdf(String latexContent, Long jobId):
  1. Create temp directory: Files.createTempDirectory("jobbot_latex_")
  2. Write latexContent to temp/resume.tex (UTF-8)
  3. Run: ProcessBuilder("pdflatex", "-interaction=nonstopmode", "-output-directory", tempDir, "resume.tex")
     - timeout: 30 seconds
     - stdout/stderr captured to temp/latex.log
  4. If exit code != 0 or PDF not generated: read temp/latex.log, throw LaTeXCompilationException(log tail)
  5. Copy tempDir/resume.pdf → <app_home>/pdfs/resume_job_{jobId}.pdf
     - Create /pdfs/ directory if not exists
  6. Delete temp directory (Files.walk + delete)
  7. Return absolute path of saved PDF

Notes:
  - pdflatex must be installed on the host machine (apt install texlive-latex-base)
  - Run pdflatex TWICE to resolve cross-references (two ProcessBuilder invocations)
  - App home resolved via System.getProperty("user.dir")
```

---

## ApplicationSubmitter — Detail

```
submit(Job job, Resume tailoredResume, UserConfig config):
  Guard 1: if applicationRepository.findByJob(job).isPresent() → return existing (already applied)
  Guard 2: if !"easy_apply".equals(job.getApplicationType()) → save Application(status="skipped") and return

  Application application = new Application();
  application.setJob(job);
  application.setUsedResume(tailoredResume);
  application.setCreatedAt(LocalDateTime.now());
  applicationRepository.save(application);   // persist as pending immediately

  String pdfPath = null;
  PlaywrightApplicationSession session = null;
  try:
    pdfPath = laTeXCompiler.compileToPdf(tailoredResume.getLatexContent(), job.getId());
    application.setGeneratedPdfPath(pdfPath);

    session = new PlaywrightApplicationSession();
    session.login(config.getLinkedInEmail(), config.getLinkedInPasswordEncrypted());
    boolean submitted = session.submitEasyApply(job.getUrl(), pdfPath, application.getCoverLetter());

    application.setStatus(submitted ? "success" : "failed");
    if (!submitted) application.setErrorReason("Easy Apply form could not be completed");
    application.setSubmittedAt(submitted ? LocalDateTime.now() : null);

  catch (LaTeXCompilationException e):
    application.setStatus("failed");
    application.setErrorReason("PDF compilation failed: " + e.getMessage());

  catch (Exception e):
    application.setStatus("failed");
    application.setErrorReason(e.getMessage());

  finally:
    if (session != null) session.closeSession();

  applicationRepository.save(application);   // update status
  return application;
```

---

## PlaywrightApplicationSession — Detail

Plain Java class (NOT `@Component`), instantiated per `submit()` call. Mirrors `PlaywrightSessionManager` pattern.

```
Fields: Playwright playwright; Browser browser; Page page;

login(String email, String password):
  1. playwright = Playwright.create()
  2. browser = playwright.chromium().launch(headless=true)
  3. page = browser.newPage()
  4. page.navigate("https://www.linkedin.com/login")
  5. page.fill("#username", email)
  6. page.fill("#password", password)
  7. page.click("[type=submit]")
  8. page.waitForURL("**/feed/**", timeout=10s)
     → if fails: throw LoginFailedException

submitEasyApply(String jobUrl, String pdfPath, String coverLetter):
  1. page.navigate(jobUrl)
  2. page.waitForSelector(".jobs-apply-button, button:has-text('Easy Apply')", timeout=5s)
  3. Click the Easy Apply button
  4. page.waitForSelector(".jobs-easy-apply-modal", timeout=5s)
     → if modal not found: return false

  Easy Apply multi-step loop (max 10 steps to prevent infinite loop):
  while (step < 10):
    if page has "Submit application" button:
      click "Submit application"
      wait for confirmation / modal close
      return true

    if page has "Next" or "Review" button:
      // Fill fields on current step
      fillFormFields(coverLetter, pdfPath)
      click "Next" / "Review"
      step++
      continue

    // Unknown step — can't proceed
    click "Dismiss" / close modal
    return false

  return false  // exhausted max steps

fillFormFields(String coverLetter, String pdfPath):
  // Cover letter text area (if present)
  Locator coverLetterField = page.locator("textarea[id*='cover-letter'], textarea[aria-label*='cover letter']").first()
  if coverLetterField.isVisible() and coverLetter != null:
    coverLetterField.fill(coverLetter)

  // Phone number (if present and empty)
  Locator phoneField = page.locator("input[id*='phone'], input[aria-label*='Phone']").first()
  if phoneField.isVisible() and phoneField.inputValue().isBlank():
    phoneField.fill(config.phoneNumber)   // see note below

  // Resume upload (if present)
  Locator fileInput = page.locator("input[type='file']").first()
  if fileInput.isVisible():
    fileInput.setInputFiles(Paths.get(pdfPath))

Note: `config.phoneNumber` — UserConfig does NOT currently have a phone field.
      Phase 3b adds `phoneNumber VARCHAR(20) NULLABLE` to `UserConfig` entity.
      If null, skip phone field.

closeSession():
  page.close(); browser.close(); playwright.close();
```

---

## UserConfig Changes

Add one nullable field to `UserConfig` entity (JPA DDL auto will add the column):

```java
@Column
private String phoneNumber;   // used when Easy Apply asks for phone
// + getter/setter
```

Also expose via existing `ConfigController`:
- `POST /api/config/setup` — already accepts a JSON body; add `phoneNumber` to the request body processing.

---

## ApplicationController

```
GET /api/applications/user/{userId}
  → 200: List<ApplicationResponse> (all Applications for this user's jobs)
  → 404: UserConfig not found

ApplicationResponse fields:
  id, jobId, jobTitle, company, status, errorReason,
  generatedPdfPath, submittedAt, createdAt
  (no coverLetter, no resumeVersionHash — keep response slim)
```

---

## Database Changes

| Table | Column | Change |
|---|---|---|
| `user_config` | `phone_number VARCHAR(20)` | New nullable column |
| `applications` | no changes | Entity already complete |

---

## Error Handling Summary

| Scenario | Application Status | Error Reason |
|---|---|---|
| Already applied | — | Return existing record |
| Not Easy Apply | `skipped` | — |
| pdflatex not installed / compile error | `failed` | LaTeX log tail |
| LinkedIn login failure | `failed` | LoginFailedException message |
| Easy Apply button not found | `failed` | "Easy Apply form could not be completed" |
| Form steps exceeded max | `failed` | "Easy Apply form could not be completed" |
| Unknown exception | `failed` | e.getMessage() |

---

## New Files

```
src/main/java/com/jobbot/
  exception/LaTeXCompilationException.java
  service/LaTeXCompiler.java
  service/PlaywrightApplicationSession.java
  service/ApplicationSubmitter.java
  controller/ApplicationController.java
  dto/ApplicationResponse.java
```

## Modified Files

```
src/main/java/com/jobbot/
  entity/UserConfig.java              — add phoneNumber field
  service/SchedulerService.java       — add application submission loop
  controller/ConfigController.java    — add phoneNumber to setup handling
  repository/ResumeRepository.java    — add findFirstByJobAndUserConfig query
```

---

## API Endpoints Added

| Method | Path | Description |
|---|---|---|
| GET | `/api/applications/user/{userId}` | List all applications for user |
