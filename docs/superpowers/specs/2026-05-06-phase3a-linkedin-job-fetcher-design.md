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
| `PlaywrightSessionManager` | Owns browser open/close and LinkedIn login. Returns a ready `Page` or throws `LoginFailedException`. | `Page createSession(email, password)` / `void closeSession()` |
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
PlaywrightSessionManager.createSession(linkedInEmail, decryptedPassword):
  1. browser = Playwright.create().chromium().launch(headless=true)
  2. page = browser.newPage()
  3. page.navigate("https://www.linkedin.com/login")
  4. page.fill("#username", linkedInEmail)   ← uses UserConfig.linkedInEmail (NOT .email)
  5. page.fill("#password", decryptedPassword)
  6. page.click("[type=submit]")
  7. page.waitForURL("**/feed/**", timeout=10s)
     → if timeout or URL contains "checkpoint" or "challenge": throw LoginFailedException
  Returns: Page (caller uses this for all subsequent navigation)

PlaywrightSessionManager.closeSession():
  Closes Page, BrowserContext, and Browser — caller must invoke in finally block.
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
  1. Load SearchConfig for user → if absent, log info, return empty list
  2. Decrypt linkedInPasswordEncrypted using existing EncryptionUtil
  3. Page page = sessionManager.createSession(userConfig.getLinkedInEmail(), decryptedPassword)
     → LoginFailedException: log error, return empty list
  4. List<Job> collected = new ArrayList<>()
     try {
       for (int pageIndex = 0; pageIndex < searchConfig.maxPages; pageIndex++):
         a. navigate to search URL with start = pageIndex * 25
         b. waitForSelector(".job-card-container", timeout=8s)
            → timeout: log warning, break loop
         c. for each card Locator:
              i.  click card to load detail panel
              ii. waitForSelector(".job-description__container", timeout=5s)
                  → timeout on this card: log warning, use empty string as description, continue
              iii.String description = page.locator(".job-description__container").innerText()
              iv. Optional<Job> job = jobParser.parseCard(card, description)
              v.  job.ifPresent(collected::add)
     } finally {
       sessionManager.closeSession()   // always closes browser
     }
  5. Return collected  ← unsaved; SchedulerService runs matcher then saves matched jobs
```

**Persistence ownership:**
- `LinkedInJobFetcher` returns an unsaved `List<Job>` — it does **not** call `jobRepository`.
- `SchedulerService` continues its existing flow: `jobMatcher.filterJobs(fetched, config)` → `jobRepository.save()` on matched jobs only.
- Deduplication is handled naturally by the `@UniqueConstraint(userConfigId, linkedInJobId)` on the `jobs` table — duplicate saves throw a constraint violation which SchedulerService's outer try/catch absorbs per job.

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

`applicationType` (String) already exists — use `"EASY_APPLY"` or `"EXTERNAL"` as values.  
`salary` (Integer) already exists — store midpoint of range or extracted value.  
`jobDescription` (TEXT) already exists — populated from job detail page.

---

## REST API

### SearchConfig Endpoints

`SearchConfig` is optional — if absent, the fetcher runs with no extra filters (all experience levels, any post date, not remote-only). Keywords and location always come from `UserConfig`.

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
| `repository/SearchConfigRepository.java` | JPA repo with `findByUserConfig` |
| `service/PlaywrightSessionManager.java` | Browser lifecycle + login |
| `service/JobParser.java` | DOM → Job mapping, salary extraction |
| `service/LinkedInJobFetcher.java` | Replace stub — full implementation |
| `controller/SearchConfigController.java` | REST CRUD for SearchConfig |

---

## Modified Files

| File | Change |
|---|---|
| `pom.xml` | Add `playwright` dependency |
| `service/SchedulerService.java` | Change `searchJobs(config, keywords, years, location)` → `fetchJobs(config)` (one line) |

---

## Error Handling Summary

| Failure | Behavior |
|---|---|
| Login failure / CAPTCHA | Log error, skip user, continue scheduler |
| Page navigation timeout | Log warning, save collected jobs, return |
| Card parse exception | Log warning, skip card, continue |
| No SearchConfig for user | Log info, skip fetching, return empty list |
| Duplicate job (already in DB) | Filtered out before `saveAll` — no exception |

---

## Playwright Dependency

Add to `pom.xml`:
```xml
<dependency>
  <groupId>com.microsoft.playwright</groupId>
  <artifactId>playwright</artifactId>
  <version>1.44.0</version>
</dependency>
```

Playwright requires browser binaries. First run: `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"` — document in README.

---

## Out of Scope (Phase 3b)

- Application submission (Easy Apply form filling)
- LinkedIn session cookie persistence
- Proxy rotation / anti-detection
- Multi-keyword search runs per user
