package com.tariffhelper.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the Llama client, bound from {@code app.llm.llama.*} in application.yml. Talks to an
 * OpenAI-compatible server: Ollama locally (no key) or a hosted provider such as Groq or Together AI (key required
 * by the provider). The base URL excludes {@code /v1}; the client appends {@code /v1/chat/completions}.
 */
@ConfigurationProperties(prefix = "app.llm.llama")
public record LlamaProperties(
        String apiKey,
        @DefaultValue("llama3.1") String model,
        @DefaultValue("http://localhost:11434") String baseUrl,
        @DefaultValue("1500") int maxTokens,
        @DefaultValue("120") int timeoutSeconds) {
}
