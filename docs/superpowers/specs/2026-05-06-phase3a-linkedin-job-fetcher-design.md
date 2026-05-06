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
| `LinkedInJobFetcher` | Orchestrates: gets session from manager, loops search pages, delegates card parsing to `JobParser`, saves to DB, always closes session in `finally`. | `List<Job> fetchJobs(UserConfig)` |
| `JobParser` | Stateless. Two public methods: `parseCard(Locator card, String jobDescription)` → `Optional<Job>`, and `extractSalary(String text)` → `Integer`. No browser knowledge. | Pure mapping logic |
| `SearchConfig` | JPA entity: per-user search parameters. Required dependency of `LinkedInJobFetcher` — not a separate subsystem. No overlap with `UserConfig` (which holds credentials + auto-apply prefs). | — |
| `SearchConfigController` | REST CRUD so users can configure search before running scheduler. | POST/PUT/GET/DELETE |

**Integration:** `SchedulerService` calls `LinkedInJobFetcher.fetchJobs(UserConfig)` — exact same call site as the stub. No changes to `SchedulerService`.

---

## Playwright Scraping Flow

### Login

```
PlaywrightSessionManager.login(page, email, decryptedPassword):
  1. page.navigate("https://www.linkedin.com/login")
  2. page.fill("#username", email)
  3. page.fill("#password", password)
  4. page.click("[type=submit]")
  5. page.waitForURL("**/feed/**", timeout=10s)
     → if timeout or URL contains "checkpoint" or "challenge": throw LoginFailedException
```

LinkedIn CAPTCHA/2FA is detected by URL pattern — bot logs the error and skips that user for the current scheduler run.

### Search URL Construction

```
Base: https://www.linkedin.com/jobs/search/
Params:
  keywords     → SearchConfig.keywords (URL-encoded)
  location     → SearchConfig.location
  f_WT=2       → remoteOnly (omitted if false)
  f_E=1,2,3    → experienceLevel mapping (ENTRY=1, MID=3, SENIOR=4, DIRECTOR=5)
  f_TPR=r86400 → PAST_DAY; r604800=PAST_WEEK; r2592000=PAST_MONTH; omitted=ANY
  start=0,25,50→ pagination offset (25 results per page)
```

### Scrape Loop

```
LinkedInJobFetcher.fetchJobs(userConfig):
  1. Load SearchConfig for user → if absent, log info, return empty list
  2. Decrypt linkedInPasswordEncrypted using existing EncryptionUtil
  3. Page page = sessionManager.createSession(email, decryptedPassword)
     → LoginFailedException: log error, return empty list
  4. try {
       For page 0..searchConfig.maxPages (default 3):
         a. navigate to search URL with start=page*25 offset
         b. waitForSelector(".job-card-container", timeout=8s)
            → timeout: log warning, break loop (save what was already collected)
         c. for each card Locator:
              i.  click card to load detail panel
              ii. waitForSelector(".job-description__container", timeout=5s)
              iii.String description = page.locator(".job-description__container").innerText()
              iv. Optional<Job> job = jobParser.parseCard(card, description)
              v.  collect non-empty results
     } finally {
       sessionManager.closeSession()  // always closes browser
     }
  5. Dedup: filter out linkedInJobIds already in JobRepository for this user
  6. Set extractedAt=now(), userConfig=userConfig on each new Job
  7. jobRepository.saveAll(newJobs)
  8. Return saved jobs list
```

**Who owns what:**
- `PlaywrightSessionManager` creates/destroys the Playwright `Browser` and `BrowserContext` — `LinkedInJobFetcher` never touches these directly.
- `LinkedInJobFetcher` owns page navigation and detail-panel click-through.
- `JobParser` receives a card `Locator` + the pre-fetched description `String` — it has no `Page` reference.

If login fails → log error, return empty list.  
If individual card parse fails → log warning, skip card, continue.  
If detail panel times out for a specific card → log warning, pass empty string as description, continue (salary will be null).

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

The method searches `text` for salary patterns in this order:

1. **Range pattern** — regex `([\d.]+)\s*[–\-to]+\s*([\d.]+)\s*LPA`: captures both bounds, returns `(low + high) / 2` as integer. Example: "₹12–18 LPA" → min=12, max=18 → returns `15`.
2. **Single value** — regex `([\d.]+)\s*LPA`: returns the matched value as integer. Example: "15 LPA" → returns `15`.
3. **USD range** — regex `\$([\d,]+)[Kk]?\s*[–\-]\s*\$([\d,]+)[Kk]?`: parse both, strip commas, handle K suffix, average. Example: "$80K–$120K" → returns `100` (stored as-is; unit noted in salary field, LPA conversion out of scope).
4. **No match** → return `null`.

---

## Data Model

### New Entity: `SearchConfig`

```java
@Entity
@Table(name = "search_config")
public class SearchConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_config_id", nullable = false, unique = true)
    private UserConfig userConfig;

    @Column(nullable = false)
    private String keywords;       // e.g. "Java backend developer"

    @Column(nullable = false)
    private String location;       // e.g. "Bengaluru"

    @Column
    private Boolean remoteOnly = false;

    @Column
    private String experienceLevel; // ENTRY | MID | SENIOR | DIRECTOR | null (any)

    @Column
    private String datePostedFilter; // PAST_DAY | PAST_WEEK | PAST_MONTH | ANY

    @Column
    private Integer maxPages = 3;  // scrape up to N result pages (25 results each)

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

`SearchConfig` is a required dependency of `LinkedInJobFetcher` — without it the fetcher has no keywords or filters. It does not duplicate `UserConfig` fields (`UserConfig` holds credentials and auto-apply preferences; `SearchConfig` holds job search parameters).

```
POST   /api/search-config
Body:  { "userConfigId": 1, "keywords": "Java developer", "location": "Bengaluru",
         "remoteOnly": false, "experienceLevel": "MID", "datePostedFilter": "PAST_WEEK",
         "maxPages": 3 }
Validation: keywords + location required; experienceLevel ∈ {ENTRY,MID,SENIOR,DIRECTOR,null};
            datePostedFilter ∈ {PAST_DAY,PAST_WEEK,PAST_MONTH,ANY}; maxPages ∈ [1..10]
Response 201: SearchConfig JSON | 409 if SearchConfig already exists for userConfigId

PUT    /api/search-config/{id}
Body:  same fields (partial update — only provided fields updated)
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
| `service/SchedulerService.java` | No signature change — fetcher now returns real jobs |

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
