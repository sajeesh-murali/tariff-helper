package com.tariffhelper.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings for the Claude client, bound from {@code app.llm.claude.*} in application.yml. */
@ConfigurationProperties(prefix = "app.llm.claude")
public record ClaudeProperties(
        String apiKey,
        @DefaultValue("claude-sonnet-5-5") String model,
        @DefaultValue("https://api.anthropic.com") String baseUrl,
        @DefaultValue("1500") int maxTokens,
        @DefaultValue("60") int timeoutSeconds) {
}
