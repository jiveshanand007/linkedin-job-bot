package com.jobbot.service;

import com.jobbot.entity.Job;
import com.jobbot.entity.JobCardData;
import com.jobbot.entity.SearchConfig;
import com.jobbot.entity.UserConfig;
import com.jobbot.exception.LoginFailedException;
import com.jobbot.repository.JobRepository;
import com.jobbot.repository.SearchConfigRepository;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LinkedInJobFetcher {

    private static final Logger logger = LoggerFactory.getLogger(LinkedInJobFetcher.class);
    private static final String SEARCH_BASE = "https://www.linkedin.com/jobs/search/";

    @Autowired private SearchConfigRepository searchConfigRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobParser jobParser;

    public List<Job> fetchJobs(UserConfig userConfig) {
        // Resolve search config; use defaults if absent
        Optional<SearchConfig> configOpt = searchConfigRepository.findByUserConfig(userConfig);
        boolean remoteOnly      = configOpt.map(c -> Boolean.TRUE.equals(c.getRemoteOnly())).orElse(false);
        String experienceLevel  = configOpt.map(SearchConfig::getExperienceLevel).orElse(null);
        String datePostedFilter = configOpt.map(c -> c.getDatePostedFilter() != null ? c.getDatePostedFilter() : "ANY").orElse("ANY");
        int maxPages            = configOpt.map(c -> c.getMaxPages() != null ? c.getMaxPages() : 3).orElse(3);

        String password = userConfig.getLinkedInPasswordEncrypted(); // used as-is; no encryption util

        List<Job> collected = new ArrayList<>();
        PlaywrightSessionManager session = null;

        try {
            session = new PlaywrightSessionManager();
            Page page = session.createSession(userConfig.getLinkedInEmail(), password);

            for (int pageIndex = 0; pageIndex < maxPages; pageIndex++) {
                String searchUrl = buildSearchUrl(userConfig, remoteOnly, experienceLevel, datePostedFilter, pageIndex);
                page.navigate(searchUrl);

                try {
                    page.waitForSelector(".job-card-container", new Page.WaitForSelectorOptions().setTimeout(8_000));
                } catch (PlaywrightException e) {
                    logger.warn("Job card container not found on page {} — stopping pagination", pageIndex);
                    break;
                }

                List<Locator> cards = page.locator(".job-card-container").all();
                logger.info("Page {}: found {} cards", pageIndex, cards.size());

                for (Locator card : cards) {
                    try {
                        // Required fields — exception here skips this card
                        String jobId = card.getAttribute("data-job-id");
                        String title = card.locator(".job-card-list__title").innerText();

                        if (jobId == null || jobId.isBlank() || title == null || title.isBlank()) {
                            continue;
                        }

                        // Optional fields — safe helpers return "" on any Playwright exception
                        String company   = safeInnerText(card, ".job-card-container__company-name");
                        String loc       = safeInnerText(card, ".job-card-container__metadata-item");
                        String url       = safeGetAttr(card, "a.job-card-list__title", "href");
                        String applyText = safeInnerText(card, ".job-card-container__apply-method");

                        // Click card to load detail panel, then extract description
                        card.click();
                        String description = "";
                        try {
                            page.waitForSelector(".job-description__container",
                                new Page.WaitForSelectorOptions().setTimeout(5_000));
                            description = page.locator(".job-description__container").innerText();
                        } catch (PlaywrightException te) {
                            logger.warn("Description panel timed out for jobId={}", jobId);
                        }

                        JobCardData data = new JobCardData(jobId, title, company, loc, url, applyText, description);
                        Optional<Job> parsed = jobParser.parseCard(data);

                        if (parsed.isPresent()) {
                            Job job = parsed.get();
                            job.setUserConfig(userConfig);
                            job.setExtractedAt(LocalDateTime.now());
                            collected.add(job);
                        }

                    } catch (Exception e) {
                        // Only Playwright structural failures reach here (required-field extraction)
                        logger.warn("Skipping card due to Playwright error: {}", e.getMessage());
                    }
                }
            }

        } catch (LoginFailedException e) {
            logger.error("LinkedIn login failed for user {}: {}", userConfig.getId(), e.getMessage());
            return List.of(); // finally still runs
        } finally {
            if (session != null) session.closeSession();
        }

        // Within-batch dedup: preserve first occurrence by linkedInJobId
        Map<String, Job> seen = new LinkedHashMap<>();
        collected.forEach(j -> seen.putIfAbsent(j.getLinkedInJobId(), j));
        List<Job> dedupedBatch = new ArrayList<>(seen.values());

        // Cross-run dedup: remove jobs already in DB for this user
        Set<String> existing = jobRepository.findLinkedInJobIdsByUserConfig(userConfig);
        List<Job> newJobs = dedupedBatch.stream()
            .filter(j -> !existing.contains(j.getLinkedInJobId()))
            .toList();

        logger.info("fetchJobs complete for user {}: {} new jobs (from {} collected)",
            userConfig.getId(), newJobs.size(), collected.size());

        return newJobs;
    }

    private String buildSearchUrl(UserConfig config, boolean remoteOnly,
                                   String experienceLevel, String datePostedFilter, int pageIndex) {
        StringBuilder url = new StringBuilder(SEARCH_BASE);
        url.append("?keywords=").append(encode(config.getJobKeywords()));
        url.append("&location=").append(encode(config.getLocation()));

        if (remoteOnly) url.append("&f_WT=2");

        if (experienceLevel != null) {
            int fE = mapExperienceLevel(experienceLevel);
            if (fE > 0) url.append("&f_E=").append(fE);
        }

        if (!"ANY".equalsIgnoreCase(datePostedFilter)) {
            url.append("&f_TPR=").append(mapDatePostedFilter(datePostedFilter));
        }

        url.append("&start=").append(pageIndex * 25);
        return url.toString();
    }

    private int mapExperienceLevel(String level) {
        return switch (level.toUpperCase()) {
            case "ENTRY"    -> 2;
            case "MID"      -> 4;
            case "SENIOR"   -> 4; // intentional: shares LinkedIn's "Mid-Senior" value
            case "DIRECTOR" -> 5;
            default -> 0; // unknown → omit param
        };
    }

    private String mapDatePostedFilter(String filter) {
        return switch (filter.toUpperCase()) {
            case "PAST_DAY"   -> "r86400";
            case "PAST_WEEK"  -> "r604800";
            case "PAST_MONTH" -> "r2592000";
            default -> "";
        };
    }

    private String encode(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String safeInnerText(Locator parent, String selector) {
        try {
            return parent.locator(selector).first().innerText();
        } catch (Exception e) {
            return "";
        }
    }

    private String safeGetAttr(Locator parent, String selector, String attr) {
        try {
            String val = parent.locator(selector).first().getAttribute(attr);
            return val != null ? val : "";
        } catch (Exception e) {
            return "";
        }
    }
}
