package com.jobbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbot.config.ClaudeApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;

@Service
public class ClaudeApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeApiClient.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 4096;

    @Autowired
    private RestTemplate claudeRestTemplate;

    @Autowired
    private ClaudeApiConfig claudeApiConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String rewriteResume(String latexContent, String jobTitle, String company, String jobDescription) {
        String prompt = buildPrompt(latexContent, jobTitle, company, jobDescription);
        String requestBody = buildRequestBody(prompt);
        HttpEntity<String> request = buildHttpEntity(requestBody);

        try {
            ResponseEntity<String> response = claudeRestTemplate.postForEntity(
                claudeApiConfig.getApiUrl(), request, String.class
            );
            return extractTextFromResponse(response.getBody());
        } catch (Exception e) {
            logger.error("Claude API call failed: {}", e.getMessage());
            throw new RuntimeException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(String latexContent, String jobTitle, String company, String jd) {
        return String.format("""
            You are a resume editor. Given a LaTeX resume and a job description, \
            rewrite ONLY these sections to better match the job:
            1. Summary / objective section
            2. Skills list (reorder to surface relevant skills first, do not add fake skills)
            3. Experience bullet points (rephrase verbs, emphasize relevant tech)

            Rules:
            - Do NOT change dates, company names, job titles, or education
            - Do NOT add experience or skills the candidate does not have
            - Keep all LaTeX commands and formatting exactly intact
            - Return ONLY the modified LaTeX, no explanation or markdown wrapper

            JOB: %s at %s
            JD: %s

            RESUME:
            %s""", jobTitle, company, jd, latexContent);
    }

    private String buildRequestBody(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                "model", claudeApiConfig.getModel(),
                "max_tokens", MAX_TOKENS,
                "messages", List.of(Map.of("role", "user", "content", prompt))
            );
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build request body", e);
        }
    }

    private HttpEntity<String> buildHttpEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", claudeApiConfig.getApiKey());
        headers.set("anthropic-version", ANTHROPIC_VERSION);
        return new HttpEntity<>(body, headers);
    }

    private String extractTextFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode textNode = root.path("content").get(0).path("text");
            if (textNode.isMissingNode()) {
                throw new RuntimeException("Unexpected Claude response structure");
            }
            return textNode.asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Claude response: " + e.getMessage(), e);
        }
    }
}
