package com.jobbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ClaudeApiConfig {

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.model}")
    private String model;

    @Value("${claude.api.url}")
    private String apiUrl;

    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getApiUrl() { return apiUrl; }

    @Bean
    @Primary
    public RestTemplate claudeRestTemplate() {
        return new RestTemplate();
    }
}
