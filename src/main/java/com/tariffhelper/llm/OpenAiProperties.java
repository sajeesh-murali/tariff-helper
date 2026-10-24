package com.tariffhelper.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings for the OpenAI client, bound from {@code app.llm.openai.*} in application.yml. */
@ConfigurationProperties(prefix = "app.llm.openai")
public record OpenAiProperties(
        String apiKey,
        @DefaultValue("gpt-4o-mini") String model,
        @DefaultValue("https://api.openai.com") String baseUrl,
        @DefaultValue("1500") int maxTokens,
        @DefaultValue("60") int timeoutSeconds) {
}
