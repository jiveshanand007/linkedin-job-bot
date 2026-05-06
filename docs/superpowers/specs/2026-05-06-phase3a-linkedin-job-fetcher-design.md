# Phase 3a — LinkedIn Job Fetcher Design

**Date:** 2026-05-06  
**Status:** Approved  
**Phase:** 3a (of 5)

---

## Problem Statement

`LinkedInJobFetcher` is currently a stub returning an empty list. The entire pipeline (tailoring, application submission) is blocked until real job data flows in. This spec covers full Playwright-based LinkedIn job search and extraction.

---

## Architecture

| Class | Responsibility | Interface |
|---|---|---|
| `PlaywrightSessionManager` | Plain Java class (NOT a Spring bean) — instantiated fresh per `fetchJobs` call by `LinkedInJobFetcher`. Owns browser open/close and LinkedIn login within a single run. | `Page createSession(linkedInEmail, password)` / `void closeSession()` |
| `LinkedInJobFetcher` | Orchestrates: gets session from manager, loops search pages, delegates card parsing to `JobParser`, returns unsaved `List<Job>`, always closes session in `finally`. | `List<Job> fetchJobs(UserConfig)` |
| `JobParser` | Stateless. Two public methods: `parseCard(Locator card, String jobDescription)` → `Optional<Job>`, and `extractSalary(String text)` → `Integer`. No browser knowledge. | Pure mapping logic |
| `SearchConfig` | JPA entity: per-user extra search filters (`remoteOnly`, `experienceLevel`, `datePostedFilter`, `maxPages`). Keywords and location are read from `UserConfig` directly — no duplication. | — |
| `SearchConfigController` | REST CRUD so users can configure search filters before running scheduler. | POST/PUT/GET/DELETE |

**Integration — SchedulerService call site changes (one line):**
```
// Before (stub):
jobFetcher.searchJobs(config, config.getJobKeywords(), config.getYearsExperienceMax(), config.getLocation())

// After (Phase 3a):
jobFetcher.fetchJobs(config)
```
`LinkedInJobFetcher` returns unsaved `List<Job>`. `SchedulerService` continues to run `jobMatcher.filterJobs()` then `jobRepository.save()` on matched jobs — **persistence ownership is unchanged**.

---

## Playwright Scraping Flow

### Login

```
PlaywrightSessionManager.createSession(linkedInEmail, password):
  // Plain Java class — instantiated once per fetchJobs call. Not a Spring singleton.
  // Holds: Playwright instance, Browser, Page as instance fields.
  1. this.playwright = Playwright.create()
  2. this.browser = playwright.chromium().launch(headless=true)
  3. this.page = browser.newPage()
  4. page.navigate("https://www.linkedin.com/login")
  5. page.fill("#username", linkedInEmail)   ← UserConfig.linkedInEmail (NOT .email)
  6. page.fill("#password", password)        ← UserConfig.linkedInPasswordEncrypted used as-is
                                              (no encryption util exists; field stores raw value)
  7. page.click("[type=submit]")
  8. page.waitForURL("**/feed/**", timeout=10s)
     → if timeout or URL contains "checkpoint" or "challenge": throw LoginFailedException
  Returns: Page (caller uses this for all subsequent navigation)

PlaywrightSessionManager.closeSession():
  Calls page.close(), browser.close(), playwright.close() — caller invokes in finally block.
  No state remains after close; the object should not be reused.
```

LinkedIn CAPTCHA/2FA is detected by URL pattern — bot logs the error and skips that user for the current scheduler run.

### Search URL Construction

```
Base: https://www.linkedin.com/jobs/search/
Params:
  keywords     → UserConfig.jobKeywords (URL-encoded)       ← from UserConfig, not SearchConfig
  location     → UserConfig.location                        ← from UserConfig, not SearchConfig
  f_WT=2       → SearchConfig.remoteOnly=true (omitted if false)
  f_E=<value>  → SearchConfig.experienceLevel mapping:
                   ENTRY=2, MID=4, SENIOR=4, DIRECTOR=5    ← LinkedIn API values (not 1/3/4/5)
                   null → param omitted (any level)
  f_TPR=r86400 → PAST_DAY; r604800=PAST_WEEK; r2592000=PAST_MONTH; omitted=ANY
  start=N      → pagination: N = pageIndex * 25, pageIndex ∈ [0, maxPages-1]
                 maxPages=3 → start=0,25,50 → up to 75 results total (exclusive upper bound)
```

### Scrape Loop

```
LinkedInJobFetcher.fetchJobs(userConfig):
  1. Load SearchConfig for user via searchConfigRepository.findByUserConfig(userConfig)
     → if absent: run with defaults (remoteOnly=false, experienceLevel=null, datePostedFilter=ANY, maxPages=3)
  2. String password = userConfig.getLinkedInPasswordEncrypted()  ← used as-is; no decryption
  3. PlaywrightSessionManager session = new PlaywrightSessionManager()
     Page page = session.createSession(userConfig.getLinkedInEmail(), password)
     → LoginFailedException: log error, return empty list
  4. List<Job> collected = new ArrayList<>()
     try {
       for (int pageIndex = 0; pageIndex < maxPages; pageIndex++):
         a. navigate to search URL with start = pageIndex * 25
         b. waitForSelector(".job-card-container", timeout=8s)
            → timeout: log warning, break loop
         c. for each card Locator:
              i.  click card to load detail panel
              ii. waitForSelector(".job-description__container", timeout=5s)
                  → timeout: log warning, set description = "", continue
              iii.String description = page.locator(".job-description__container").innerText()
              iv. Optional<Job> job = jobParser.parseCard(card, description)
              v.  if present: set job.setUserConfig(userConfig); job.setExtractedAt(LocalDateTime.now())
              vi. job.ifPresent(collected::add)
     } finally {
       session.closeSession()
     }
  5. Pre-save dedup: fetch existing linkedInJobIds for this user in one query:
       Set<String> existing = jobRepository.findLinkedInJobIdsByUserConfig(userConfig)
       List<Job> newJobs = collected.stream()
         .filter(j -> !existing.contains(j.getLinkedInJobId()))
         .toList()
  6. Return newJobs  ← unsaved; SchedulerService runs matcher then saves matched jobs
```

**Persistence ownership:**
- `LinkedInJobFetcher` returns unsaved `List<Job>` — it does **not** call `jobRepository.save()`.
- `SchedulerService` flow is unchanged: `jobMatcher.filterJobs(fetched, config)` → `jobRepository.save()` per matched job.
- Dedup is done in step 5 (pre-save lookup). No reliance on constraint exceptions.
- New repository method required: `Set<String> findLinkedInJobIdsByUserConfig(UserConfig)` — added to `JobRepository`.

---

## JobParser

Stateless `@Component`. Two public methods, no browser dependencies.

**`Optional<Job> parseCard(Locator card, String jobDescription)`**

| Field | DOM Selector | Fallback |
|---|---|---|
| `linkedInJobId` | `data-job-id` attribute on card root element | return `Optional.empty()` (required) |
| `title` | `.job-card-list__title` inner text | return `Optional.empty()` (required) |
| `company` | `.job-card-container__company-name` | `"Unknown"` |
| `location` | `.job-card-container__metadata-item` (first match) | `""` |
| `url` | `href` of `a.job-card-list__title` | `""` |
| `applicationType` | `.job-card-container__apply-method` text contains "Easy Apply" → `"EASY_APPLY"` | `"EXTERNAL"` |
| `jobDescription` | passed-in `jobDescription` String directly | `""` |
| `salary` | `extractSalary(jobDescription)` — see below | `null` |

**`Integer extractSalary(String text)`**

All salaries are normalised to **LPA** before storage so that `UserConfig.minSalaryLPA` filtering is consistent regardless of the job's original currency. USD is converted using a fixed constant `USD_ANNUAL_TO_LPA = 0.083` (≈ $1 = ₹83, 1 LPA = ₹1,00,000).

The method searches `text` for salary patterns in this order:

1. **INR range (LPA)** — regex `([\d.]+)\s*[–\-to]+\s*([\d.]+)\s*LPA`: average the two bounds → store as integer. Example: "₹12–18 LPA" → `(12+18)/2` → `15`.
2. **INR single (LPA)** — regex `([\d.]+)\s*LPA`: use value directly. Example: "15 LPA" → `15`.
3. **USD range** — regex `\$([\d,]+)[Kk]?\s*[–\-]\s*\$([\d,]+)[Kk]?`: strip commas, expand K suffix (×1000), average the two bounds, then multiply by `USD_ANNUAL_TO_LPA`. Example: "$80K–$120K" → avg=$100,000 → `100000 × 0.083` → `83` LPA.
4. **USD single** — regex `\$([\d,]+)[Kk]?`: same conversion. Example: "$90K" → `90000 × 0.083` → `74` LPA.
5. **No match** → return `null`.

`USD_ANNUAL_TO_LPA = 0.083` is a constant in `JobParser` — easy to update if needed.

---

## Data Model

### New Entity: `SearchConfig`

`SearchConfig` holds extra search filters only. Keywords and location come from `UserConfig` (single source of truth — no duplication).

```java
@Entity
@Table(name = "search_config")
public class SearchConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_config_id", nullable = false, unique = true)
    private UserConfig userConfig;

    @Column
    private Boolean remoteOnly = false;

    @Column
    private String experienceLevel; // ENTRY | MID | SENIOR | DIRECTOR | null = any

    @Column
    private String datePostedFilter; // PAST_DAY | PAST_WEEK | PAST_MONTH | ANY (default ANY)

    @Column
    private Integer maxPages = 3;   // [1..10]; 3 → up to 75 results per run

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;
}
```

### `Job` Entity — No Changes

`applicationType` (String) — use `"EASY_APPLY"` or `"EXTERNAL"`.  
`salary` (Integer) — store LPA midpoint or converted LPA value.  
`jobDescription` (TEXT) — populated from job detail panel text.  
`userConfig` + `extractedAt` — set by `LinkedInJobFetcher` in scrape loop (step 4-v) before returning.

---

## REST API

### SearchConfig Endpoints

`SearchConfig` is optional. If absent the fetcher runs with default filters: `remoteOnly=false`, `experienceLevel=null` (any), `datePostedFilter=ANY`, `maxPages=3`.

```
POST   /api/search-config
Body:  { "userConfigId": 1, "remoteOnly": false, "experienceLevel": "MID",
         "datePostedFilter": "PAST_WEEK", "maxPages": 3 }
Validation: userConfigId required; all filter fields optional with defaults shown above;
            experienceLevel ∈ {ENTRY,MID,SENIOR,DIRECTOR,null};
            datePostedFilter ∈ {PAST_DAY,PAST_WEEK,PAST_MONTH,ANY};
            maxPages ∈ [1..10]
Response 201: SearchConfig JSON | 409 if SearchConfig already exists for userConfigId

PUT    /api/search-config/{id}
Body:  any subset of filter fields (only provided fields updated; omitted fields unchanged)
       No required fields — all are optional for partial update
Response 200: updated SearchConfig JSON | 404 if not found

GET    /api/search-config/user/{userConfigId}
Response 200: SearchConfig JSON | 404 if none configured

DELETE /api/search-config/{id}
Response 204 No Content | 404 if not found
```

---

## New Files

| File | Purpose |
|---|---|
| `entity/SearchConfig.java` | New JPA entity |
| `repository/SearchConfigRepository.java` | `Optional<SearchConfig> findByUserConfig(UserConfig)` |
| `repository/JobRepository.java` | Add `@Query` method: `Set<String> findLinkedInJobIdsByUserConfig(UserConfig)` |
| `service/PlaywrightSessionManager.java` | Plain Java class — browser lifecycle + login, not a Spring bean |
| `service/JobParser.java` | `@Component` — DOM → Job mapping, salary extraction |
| `service/LinkedInJobFetcher.java` | Replace stub — full implementation |
| `controller/SearchConfigController.java` | REST CRUD for SearchConfig |

---

## Modified Files

| File | Change |
|---|---|
| `pom.xml` | No change — Playwright 1.40.0 already present |
| `service/SchedulerService.java` | Change `searchJobs(config, keywords, years, location)` → `fetchJobs(config)` (one line) |

---

## Error Handling Summary

| Failure | Behavior |
|---|---|
| Login failure / CAPTCHA | Log error, skip user, continue scheduler |
| Page navigation timeout | Log warning, save collected jobs, return |
| Card parse exception | Log warning, skip card, continue |
| No SearchConfig for user | Run with defaults: remoteOnly=false, experienceLevel=any, datePostedFilter=ANY, maxPages=3 |
| Duplicate job (pre-save lookup) | Filtered out in step 5 via `findLinkedInJobIdsByUserConfig` — no DB exceptions |

---

## Playwright Dependency

Already present in `pom.xml` at version 1.40.0 — no changes needed.

Playwright requires browser binaries to be installed separately. On first setup run: `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"` — document in README.

---

## Out of Scope (Phase 3b)

- Application submission (Easy Apply form filling)
- LinkedIn session cookie persistence
- Proxy rotation / anti-detection
- Multi-keyword search runs per user
