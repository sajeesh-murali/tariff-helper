package com.tariffhelper.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the OpenRouter client, bound from {@code app.llm.openrouter.*} in application.yml. OpenRouter speaks
 * the OpenAI chat protocol. The base URL excludes {@code /v1}; the client appends {@code /v1/chat/completions}.
 */
@ConfigurationProperties(prefix = "app.llm.openrouter")
public record OpenRouterProperties(
        String apiKey,
        @DefaultValue("meta-llama/llama-3.3-70b-instruct") String model,
        @DefaultValue("https://openrouter.ai/api") String baseUrl,
        @DefaultValue("1500") int maxTokens,
        @DefaultValue("60") int timeoutSeconds) {
}
