# Phase 4 — Full Automation (Scheduled Pipeline)

**Date:** 2026-05-06  
**Status:** Draft  
**Phase:** 4 (of 5)

---

## Problem Statement

The pipeline (fetch → filter → tailor → apply) currently only runs on-demand via `POST /api/scheduler/run`. Phase 4 makes it run automatically on a fixed hourly schedule for all opted-in users. `SchedulerController.start()` and `.stop()` become functional.

---

## Approach

Use Spring `@Scheduled` — simple, single-thread, fixed-rate. No Quartz dependency.

- `@Scheduled` task runs every hour for all users where `schedulerActive = true`
- `start(userId)` → sets `UserConfig.schedulerActive = true`
- `stop(userId)` → sets `UserConfig.schedulerActive = false`
- `autoApplyEnabled` (existing) remains the guard for the apply step; `schedulerActive` controls whether the scheduler picks up the user at all

---

## Architecture

| Component | Change |
|---|---|
| `UserConfig` entity | Add `schedulerActive BOOLEAN DEFAULT false` column |
| `SchedulerService` | Add `@Scheduled(cron = "0 0 * * * *")` method `runScheduledJobs()` — finds all users with `schedulerActive=true`, calls `executeRun(userId)` for each |
| `SchedulerController` | `start()` sets `schedulerActive=true` + saves; `stop()` sets `schedulerActive=false` + saves |
| `LinkedInJobBotApplication` | Add `@EnableScheduling` annotation |
| `UserConfigRepository` | Add `findBySchedulerActiveTrue()` query |

---

## SchedulerService Changes

```java
// New method — Spring calls this every hour at :00
@Scheduled(cron = "0 0 * * * *")
public void runScheduledJobs() {
    List<UserConfig> activeUsers = userConfigRepository.findBySchedulerActiveTrue();
    logger.info("Scheduled run triggered. Active users: {}", activeUsers.size());
    for (UserConfig user : activeUsers) {
        try {
            logger.info("Running scheduled pipeline for user {}", user.getId());
            executeRun(user.getId());
        } catch (Exception e) {
            logger.error("Scheduled run failed for user {}", user.getId(), e);
            // continue to next user — don't let one failure stop others
        }
    }
}
```

`executeRun(Long userId)` is unchanged — the scheduler simply calls the same method as the manual endpoint.

---

## SchedulerController Changes

```java
@PostMapping("/start")
public ResponseEntity<?> startScheduler(@RequestParam Long userId) {
    UserConfig config = userConfigRepository.findById(userId)
        .orElse(null);
    if (config == null) return ResponseEntity.notFound().build();
    config.setSchedulerActive(true);
    userConfigRepository.save(config);
    logger.info("Scheduler activated for user {}", userId);
    return ResponseEntity.ok(Map.of("status", "started", "userId", userId));
}

@PostMapping("/stop")
public ResponseEntity<?> stopScheduler(@RequestParam Long userId) {
    UserConfig config = userConfigRepository.findById(userId)
        .orElse(null);
    if (config == null) return ResponseEntity.notFound().build();
    config.setSchedulerActive(false);
    userConfigRepository.save(config);
    logger.info("Scheduler deactivated for user {}", userId);
    return ResponseEntity.ok(Map.of("status", "stopped", "userId", userId));
}
```

---

## Status Endpoint

Add one new endpoint to expose scheduler state without requiring a full run:

```
GET /api/scheduler/status?userId={userId}
→ 200: { "schedulerActive": true, "autoApplyEnabled": false, "userId": 1 }
→ 404: user not found
```

---

## UserConfig Changes

Add one boolean field (JPA DDL auto creates column):

```java
@Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
private boolean schedulerActive = false;
// + getter/setter
```

---

## Concurrency Safety

`@Scheduled` runs on a single thread (Spring's default). This means:
- One user's run completes before the next starts
- No two runs for the same user overlap (by design)
- Long-running Playwright sessions are acceptable (they block the thread, but only one user runs at a time)

If the pipeline for user A takes >1 hour, user B's scheduled run is delayed until A finishes. This is acceptable for Phase 4 — a proper thread pool or Quartz can be added later if needed.

---

## New Files

```
src/main/java/com/jobbot/
  (none — all changes are modifications to existing files)
```

## Modified Files

```
src/main/java/com/jobbot/
  LinkedInJobBotApplication.java       — add @EnableScheduling
  entity/UserConfig.java               — add schedulerActive field
  repository/UserConfigRepository.java — add findBySchedulerActiveTrue()
  service/SchedulerService.java        — add @Scheduled runScheduledJobs()
  controller/SchedulerController.java  — implement start() and stop()
```

---

## Database Changes

| Table | Column | Change |
|---|---|---|
| `user_config` | `scheduler_active BOOLEAN DEFAULT false` | New column |

---

## API Endpoints Changed

| Method | Path | Before | After |
|---|---|---|---|
| POST | `/api/scheduler/start?userId=X` | Returns stub message | Sets `schedulerActive=true`, persists |
| POST | `/api/scheduler/stop?userId=X` | Returns `{status: stopped}` | Sets `schedulerActive=false`, persists |
| GET | `/api/scheduler/status?userId=X` | **New** | Returns scheduler + autoApply state |

---

## Pipeline Flow (Phase 4 Complete)

```
[Every hour — @Scheduled]
  → find users where schedulerActive=true
  → for each user:
      executeRun(userId)
        ├─ LinkedInJobFetcher.fetchJobs()        [Phase 3a]
        ├─ JobMatcher.filterJobs()               [Phase 1]
        ├─ jobRepository.saveAll()               [Phase 1]
        ├─ ResumeTailor.tailorAndSave() per job  [Phase 2]
        └─ if autoApplyEnabled:
             ApplicationSubmitter.submit() per job  [Phase 3b]

[On-demand]
  POST /api/scheduler/run?userId=X → same executeRun() call
```
