package com.jobbot.service;

import com.jobbot.exception.LoginFailedException;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaywrightApplicationSession {

    private static final Logger logger = LoggerFactory.getLogger(PlaywrightApplicationSession.class);

    private Playwright playwright;
    private Browser browser;
    private Page page;

    public void login(String email, String password) {
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        this.page = browser.newPage();

        page.navigate("https://www.linkedin.com/login");
        page.fill("#username", email);
        page.fill("#password", password);
        page.click("[type=submit]");

        try {
            page.waitForURL("**/feed/**", new Page.WaitForURLOptions().setTimeout(10_000));
        } catch (PlaywrightException e) {
            throw new LoginFailedException("LinkedIn login failed or challenge detected");
        }
    }

    public boolean submitEasyApply(String jobUrl, String pdfPath, String coverLetter) {
        page.navigate(jobUrl);

        try {
            page.waitForSelector(".jobs-apply-button, button:has-text('Easy Apply')",
                    new Page.WaitForSelectorOptions().setTimeout(5_000));
        } catch (PlaywrightException e) {
            logger.warn("Easy Apply button not found for: {}", jobUrl);
            return false;
        }

        page.locator(".jobs-apply-button, button:has-text('Easy Apply')").first().click();

        try {
            page.waitForSelector(".jobs-easy-apply-modal",
                    new Page.WaitForSelectorOptions().setTimeout(5_000));
        } catch (PlaywrightException e) {
            logger.warn("Easy Apply modal did not open for: {}", jobUrl);
            return false;
        }

        for (int step = 0; step < 10; step++) {
            Locator submitButton = page.locator(
                    "button[aria-label='Submit application'], button:has-text('Submit application')");
            if (submitButton.count() > 0 && submitButton.first().isVisible()) {
                submitButton.first().click();
                page.waitForTimeout(3_000);
                logger.info("Application submitted for: {}", jobUrl);
                return true;
            }

            Locator nextOrReviewButton = page.locator(
                    "button:has-text('Next'), button:has-text('Review')");
            if (nextOrReviewButton.count() > 0 && nextOrReviewButton.first().isVisible()) {
                fillFormFields(coverLetter, pdfPath);
                nextOrReviewButton.first().click();
                page.waitForTimeout(1_000);
                continue;
            }

            // No recognisable button — dismiss modal and give up
            Locator dismissButton = page.locator(
                    "button[aria-label='Dismiss'], .artdeco-modal__dismiss");
            if (dismissButton.count() > 0) {
                dismissButton.first().click();
            }
            logger.warn("Unrecognised modal state at step {} for: {}", step, jobUrl);
            return false;
        }

        logger.warn("Exceeded max steps (10) for Easy Apply on: {}", jobUrl);
        return false;
    }

    private void fillFormFields(String coverLetter, String pdfPath) {
        try {
            Locator coverLetterField = page.locator(
                    "textarea[id*='cover-letter'], textarea[aria-label*='cover letter']").first();
            if (coverLetterField.isVisible() && coverLetter != null && !coverLetter.isBlank()) {
                coverLetterField.fill(coverLetter);
            }
        } catch (Exception e) {
            logger.warn("Failed to fill cover letter field: {}", e.getMessage());
        }

        try {
            // Phone fill skipped here — ApplicationSubmitter handles phone if needed
            Locator phoneField = page.locator(
                    "input[id*='phone'], input[aria-label*='Phone']").first();
            if (phoneField.isVisible() && phoneField.inputValue().trim().isEmpty()) {
                // phone number comes from UserConfig — skip fill here
            }
        } catch (Exception e) {
            logger.warn("Failed to check phone field: {}", e.getMessage());
        }

        try {
            Locator fileInput = page.locator("input[type='file']").first();
            if (fileInput.isVisible()) {
                fileInput.setInputFiles(java.nio.file.Paths.get(pdfPath));
            }
        } catch (Exception e) {
            logger.warn("Failed to upload file: {}", e.getMessage());
        }
    }

    public void closeSession() {
        try { if (page != null) page.close(); } catch (Exception ignored) {}
        try { if (browser != null) browser.close(); } catch (Exception ignored) {}
        try { if (playwright != null) playwright.close(); } catch (Exception ignored) {}
    }
}
