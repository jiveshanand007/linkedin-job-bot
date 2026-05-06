package com.jobbot.service;

import com.jobbot.entity.Job;
import com.jobbot.entity.JobCardData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JobParser {

    private static final Logger logger = LoggerFactory.getLogger(JobParser.class);

    // USD annual salary → approximate LPA  (≈ $1 = ₹83, 1 LPA = ₹1,00,000)
    private static final double USD_ANNUAL_TO_LPA = 0.083;

    // Salary regex patterns (tried in order)
    private static final Pattern INR_RANGE  = Pattern.compile("([\\d.]+)\\s*[\\u2013\\-to]+\\s*([\\d.]+)\\s*LPA", Pattern.CASE_INSENSITIVE);
    private static final Pattern INR_SINGLE = Pattern.compile("([\\d.]+)\\s*LPA", Pattern.CASE_INSENSITIVE);
    private static final Pattern USD_RANGE  = Pattern.compile("\\$([\\d,]+[Kk]?)\\s*[\\u2013\\-]\\s*\\$([\\d,]+[Kk]?)");
    private static final Pattern USD_SINGLE = Pattern.compile("\\$([\\d,]+[Kk]?)");

    /**
     * Maps a JobCardData (plain strings from the DOM) to a Job entity.
     * Never throws. Returns Optional.empty() if linkedInJobId or title are blank.
     */
    public Optional<Job> parseCard(JobCardData data) {
        try {
            if (isBlank(data.linkedInJobId()) || isBlank(data.title())) {
                return Optional.empty();
            }

            Job job = new Job();
            job.setLinkedInJobId(data.linkedInJobId().trim());
            job.setTitle(data.title().trim());
            job.setCompany(isBlank(data.company()) ? "Unknown" : data.company().trim());
            job.setLocation(isBlank(data.location()) ? "" : data.location().trim());
            job.setUrl(isBlank(data.url()) ? "" : data.url().trim());
            job.setJobDescription(isBlank(data.jobDescription()) ? "" : data.jobDescription());
            job.setApplicationType(
                data.applyMethod() != null && data.applyMethod().contains("Easy Apply")
                    ? "EASY_APPLY" : "EXTERNAL"
            );
            job.setSalary(extractSalary(data.jobDescription()));

            return Optional.of(job);
        } catch (Exception e) {
            logger.warn("JobParser.parseCard() unexpected error (should never throw): {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts salary from free text and normalises to LPA (integer).
     * Returns null if no salary pattern found.
     */
    public Integer extractSalary(String text) {
        if (isBlank(text)) return null;

        // 1. INR range: e.g. "₹12–18 LPA" or "12-18 LPA"
        Matcher m = INR_RANGE.matcher(text);
        if (m.find()) {
            double low  = Double.parseDouble(m.group(1));
            double high = Double.parseDouble(m.group(2));
            return (int) Math.round((low + high) / 2.0);
        }

        // 2. INR single: e.g. "15 LPA"
        m = INR_SINGLE.matcher(text);
        if (m.find()) {
            return (int) Math.round(Double.parseDouble(m.group(1)));
        }

        // 3. USD range: e.g. "$80K–$120K"
        m = USD_RANGE.matcher(text);
        if (m.find()) {
            double low  = parseUsdValue(m.group(1));
            double high = parseUsdValue(m.group(2));
            double avgUsd = (low + high) / 2.0;
            return (int) Math.round(avgUsd * USD_ANNUAL_TO_LPA);
        }

        // 4. USD single: e.g. "$90K"
        m = USD_SINGLE.matcher(text);
        if (m.find()) {
            double usd = parseUsdValue(m.group(1));
            return (int) Math.round(usd * USD_ANNUAL_TO_LPA);
        }

        return null;
    }

    // Strips commas; multiplies by 1000 if K/k suffix present in captured string
    private double parseUsdValue(String raw) {
        boolean hasK = raw.toLowerCase().endsWith("k");
        String cleaned = raw.replace(",", "").replaceAll("[Kk]$", "");
        double val = Double.parseDouble(cleaned);
        return hasK ? val * 1000 : val;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
