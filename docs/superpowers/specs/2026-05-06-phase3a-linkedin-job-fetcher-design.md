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
| `JobParser` | Stateless `@Component`. No Playwright dependency — receives pre-extracted data as a plain `JobCardData` record (no `Locator`, no `Page`). Two public methods: `parseCard(JobCardData)` → `Optional<Job>`, and `extractSalary(String text)` → `Integer`. | Pure string/regex logic only |
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
                   ENTRY=2, MID=4, SENIOR=4, DIRECTOR=5
                   MID and SENIOR intentionally share value 4 (LinkedIn's "Mid-Senior level")
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
  3. List<Job> collected = new ArrayList<>()
     PlaywrightSessionManager session = new PlaywrightSessionManager()
     try {
       // session.createSession() is inside try so finally always closes session
       Page page = session.createSession(userConfig.getLinkedInEmail(), password)
       // LoginFailedException thrown here is caught below

       for (int pageIndex = 0; pageIndex < maxPages; pageIndex++):
         a. navigate to search URL with start = pageIndex * 25
         b. waitForSelector(".job-card-container", timeout=8s)
            → PlaywrightException/timeout: log warning, break loop
         c. for each card Locator:
              try {
                // Step 1: extract card-level strings (Playwright exceptions caught here)
                String jobId      = card.getAttribute("data-job-id")
                String title      = card.locator(".job-card-list__title").innerText()
                String company    = card.locator(".job-card-container__company-name").innerText()
                String loc        = card.locator(".job-card-container__metadata-item").first().innerText()
                String url        = card.locator("a.job-card-list__title").getAttribute("href")
                String applyText  = card.locator(".job-card-container__apply-method").innerText()

                // Step 2: click card, load detail panel, extract description
                card.click()
                String description = ""
                try {
                  page.waitForSelector(".job-description__container", timeout=5s)
                  description = page.locator(".job-description__container").innerText()
                } catch (TimeoutError te) {
                  log.warn("Description panel timed out for jobId={}", jobId)
                  // description stays ""
                }

                // Step 3: pass plain strings to JobParser (no Playwright types cross this boundary)
                JobCardData data = new JobCardData(jobId, title, company, loc, url, applyText, description)
                Optional<Job> job = jobParser.parseCard(data)
                // JobParser validates semantics (blank required field → empty Optional)
                // The outer try/catch handled Playwright structural failures above

                if (job.isPresent()) {
                  job.get().setUserConfig(userConfig)
                  job.get().setExtractedAt(LocalDateTime.now())
                  collected.add(job.get())
                }
              } catch (Exception e) {
                // Only Playwright structural failures reach here.
                // jobParser.parseCard() is guaranteed never to throw — it returns
                // Optional.empty() for all invalid/blank data internally.
                log.warn("Skipping card due to Playwright error: {}", e.getMessage())
                // continue to next card
              }

     } catch (LoginFailedException e) {
       log.error("LinkedIn login failed for user {}: {}", userConfig.getId(), e.getMessage())
       return List.of()   // finally still runs — session is closed
     } finally {
       session.closeSession()  // idempotent; safe even if createSession() threw
     }
  4. Within-batch dedup: dedupe collected list by linkedInJobId (preserve first occurrence):
       Map<String,Job> seen = new LinkedHashMap<>()
       collected.forEach(j -> seen.putIfAbsent(j.getLinkedInJobId(), j))
       List<Job> dedupedBatch = new ArrayList<>(seen.values())
  5. Cross-run dedup: remove jobs already in DB:
       Set<String> existing = jobRepository.findLinkedInJobIdsByUserConfig(userConfig)
       List<Job> newJobs = dedupedBatch.stream()
         .filter(j -> !existing.contains(j.getLinkedInJobId()))
         .toList()
  6. Return newJobs  ← unsaved; SchedulerService runs matcher then saves matched jobs
```

**Responsibility split — scrape loop vs JobParser:**
- The per-card `catch (Exception e)` catches **Playwright structural failures only** (element not found, DOM API errors, navigation timeout). `jobParser.parseCard()` is placed after all Playwright calls and is **guaranteed never to throw** — it handles all edge cases internally and returns `Optional.empty()` for invalid data.
- `JobParser.parseCard(JobCardData)` handles **semantic validation**: blank `linkedInJobId` or `title` → `Optional.empty()`; blank optional fields → fallback value. No exceptions propagate out of this method.
- Missing DOM selectors for optional fields (company, location, url, applyMethod) are handled as blank strings by the scrape loop's per-card catch — these are treated as blank data, not structural failures, so they reach JobParser with empty strings and the fallback values are applied.

**Persistence ownership:**
- `LinkedInJobFetcher` returns unsaved `List<Job>` — it does **not** call `jobRepository.save()`.
- `SchedulerService` flow is unchanged: `jobMatcher.filterJobs(fetched, config)` → `jobRepository.save()` per matched job.
- Dedup is done in two steps: (5) within-batch via `LinkedHashMap`, (6) cross-run via DB lookup. No reliance on constraint exceptions.
- New repository method required: `Set<String> findLinkedInJobIdsByUserConfig(UserConfig)` — added to `JobRepository`.

---

## JobParser

Stateless `@Component`. Zero Playwright imports — depends only on the `JobCardData` record and `Job` entity.

**`JobCardData` record** (plain Java record, no Playwright types):
```java
record JobCardData(
    String linkedInJobId,
    String title,
    String company,
    String location,
    String url,
    String applyMethod,    // raw text from DOM, e.g. "Easy Apply" or empty
    String jobDescription  // full text from detail panel, may be empty string
)
```

**`Optional<Job> parseCard(JobCardData data)`**

| Field | Source | Fallback |
|---|---|---|
| `linkedInJobId` | `data.linkedInJobId()` | return `Optional.empty()` if blank (required) |
| `title` | `data.title()` | return `Optional.empty()` if blank (required) |
| `company` | `data.company()` | `"Unknown"` if blank |
| `location` | `data.location()` | `""` if blank |
| `url` | `data.url()` | `""` if blank |
| `applicationType` | `data.applyMethod().contains("Easy Apply")` → `"EASY_APPLY"` | `"EXTERNAL"` |
| `jobDescription` | `data.jobDescription()` | `""` if blank |
| `salary` | `extractSalary(data.jobDescription())` | `null` |

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
    private String datePostedFilter = "ANY"; // PAST_DAY | PAST_WEEK | PAST_MONTH | ANY
                                              // null treated as "ANY" (f_TPR param omitted)

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

`SearchConfig` is optional. If absent the fetcher runs with default filters: `remoteOnly=false`, `experienceLevel=null` (any), `datePostedFilter="ANY"`, `maxPages=3`.

**DTO strategy:** Controller uses two simple DTOs to avoid exposing the nested `UserConfig` object (which contains `linkedInPasswordEncrypted`):

- **`SearchConfigRequest`** — input DTO: `{ userConfigId: Long, remoteOnly: Boolean, experienceLevel: String, datePostedFilter: String, maxPages: Integer }`
- **`SearchConfigResponse`** — output DTO: `{ id: Long, userConfigId: Long, remoteOnly: Boolean, experienceLevel: String, datePostedFilter: String, maxPages: Integer, createdAt: String (ISO-8601), updatedAt: String (ISO-8601) }` — no nested `UserConfig`, no password field

Controller maps: `SearchConfigRequest` → resolves `UserConfig` by `userConfigId` → builds `SearchConfig` entity → sets `createdAt = updatedAt = LocalDateTime.now()` on POST, `updatedAt = LocalDateTime.now()` on PUT → saves → maps to `SearchConfigResponse`.

```
POST   /api/search-config
Body (SearchConfigRequest):
  { "userConfigId": 1, "remoteOnly": false, "experienceLevel": "MID",
    "datePostedFilter": "PAST_WEEK", "maxPages": 3 }
Validation: userConfigId required and must exist; all filter fields optional with defaults;
            experienceLevel ∈ {ENTRY,MID,SENIOR,DIRECTOR,null};
            datePostedFilter ∈ {PAST_DAY,PAST_WEEK,PAST_MONTH,ANY,null→ANY};
            maxPages ∈ [1..10]
Response 201: SearchConfigResponse JSON | 404 if userConfigId not found | 409 if already exists

PUT    /api/search-config/{id}
Body:  any subset of filter fields. Field update semantics:
         - Field absent from JSON body → value unchanged
         - Field present as null → reset to default (experienceLevel=null means "any", datePostedFilter=null→"ANY")
       userConfigId is never updatable via PUT.
Response 200: SearchConfigResponse JSON | 404 if not found

GET    /api/search-config/user/{userConfigId}
Response 200: SearchConfigResponse JSON | 404 if none configured

DELETE /api/search-config/{id}
Response 204 No Content | 404 if not found
```

---

## New Files

| File | Purpose |
|---|---|
| `exception/LoginFailedException.java` | Unchecked exception (`extends RuntimeException`) — thrown by `PlaywrightSessionManager.createSession()` on CAPTCHA/2FA/timeout |
| `entity/JobCardData.java` | Plain Java record — bridge between scrape loop and JobParser, no Playwright types |
| `entity/SearchConfig.java` | New JPA entity |
| `dto/SearchConfigRequest.java` | Input DTO: userConfigId + filter fields |
| `dto/SearchConfigResponse.java` | Output DTO: filter fields + userConfigId, no nested UserConfig/password |
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
| `README.md` | Add Playwright browser install command to setup instructions |

---

## Error Handling Summary

| Failure | Behavior |
|---|---|
| Login failure / CAPTCHA | `LoginFailedException` thrown in try block → catch logs error and returns `List.of()` → finally closes session (Java guarantees finally runs even after return in catch) |
| Page navigation timeout | Log warning, save collected jobs, return |
| Card DOM extraction or parse error | Per-card `try/catch` — log warning, skip card, continue loop |
| Within-batch duplicate (same job on multiple pages) | Deduped in step 5 via `LinkedHashMap` keyed on `linkedInJobId` |
| Duplicate job (pre-save lookup) | Filtered out in step 6 via `findLinkedInJobIdsByUserConfig` — no DB exceptions |

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
