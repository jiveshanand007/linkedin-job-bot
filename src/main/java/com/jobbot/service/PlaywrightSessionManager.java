package com.jobbot.service;

import com.jobbot.exception.LoginFailedException;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaywrightSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(PlaywrightSessionManager.class);

    private Playwright playwright;
    private Browser browser;
    private Page page;

    /**
     * Opens a headless Chromium browser, navigates to LinkedIn login, and authenticates.
     * Uses UserConfig.linkedInEmail and UserConfig.linkedInPasswordEncrypted (stored as plain text).
     *
     * @throws LoginFailedException if login fails (CAPTCHA, 2FA, timeout, or challenge page detected)
     */
    public Page createSession(String linkedInEmail, String password) {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();

        try {
            page.navigate("https://www.linkedin.com/login");
            page.fill("#username", linkedInEmail);
            page.fill("#password", password);
            page.click("[type=submit]");

            page.waitForURL("**/feed/**", new Page.WaitForURLOptions().setTimeout(10_000));

            String currentUrl = page.url();
            if (currentUrl.contains("checkpoint") || currentUrl.contains("challenge")) {
                throw new LoginFailedException("LinkedIn challenge page detected: " + currentUrl);
            }

            logger.info("LinkedIn login successful for: {}", linkedInEmail);
            return page;

        } catch (PlaywrightException e) {
            throw new LoginFailedException("LinkedIn login timed out or failed: " + e.getMessage());
        }
    }

    /**
     * Closes Page, Browser, and Playwright instance. Idempotent — safe to call even if
     * createSession() never completed.
     */
    public void closeSession() {
        try { if (page != null) page.close(); } catch (Exception ignored) {}
        try { if (browser != null) browser.close(); } catch (Exception ignored) {}
        try { if (playwright != null) playwright.close(); } catch (Exception ignored) {}
    }
}
