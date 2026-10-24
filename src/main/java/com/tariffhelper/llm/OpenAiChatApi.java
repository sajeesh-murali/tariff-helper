package com.tariffhelper.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One call to an OpenAI-style {@code /v1/chat/completions} endpoint. Used for OpenAI itself and for
 * Llama served by Ollama, which speaks the same protocol.
 *
 * Privacy: prompts and model answers are never logged. Only status codes and token counts are.
 */
public class OpenAiChatApi implements ChatApi {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatApi.class);

    private final RestClient restClient;
    private final String provider;
    private final String model;
    private final String apiKey;
    private final int maxTokens;
    private final String maxTokensField;

    /**
     * @param provider       name used in log lines and error messages
     * @param apiKey         bearer token; blank means no Authorization header
     * @param maxTokensField request field for the limit: OpenAI's newer models need "max_completion_tokens",
     *                       Ollama and most compatible servers use "max_tokens"
     */
    public OpenAiChatApi(RestClient restClient, String provider, String model, String apiKey,
                         int maxTokens, String maxTokensField) {
        this.restClient = restClient;
        this.provider = provider;
        this.model = model;
        this.apiKey = apiKey;
        this.maxTokens = maxTokens;
        this.maxTokensField = maxTokensField;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public int maxTokens() {
        return maxTokens;
    }

    @Override
    public String send(String system, String user, int maxTokens) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put(maxTokensField, maxTokens);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));

        JsonNode response;
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                request = request.header("Authorization", "Bearer " + apiKey);
            }
            response = request.body(payload).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.error("{} API returned an error: status={}", provider, status);
            throw new LlmException("The AI service returned an error (HTTP " + status + ")");
        } catch (RestClientException e) {
            log.error("{} API call failed: {}", provider, e.getClass().getSimpleName());
            throw new LlmException("Could not reach the AI service");
        }

        if (response == null) {
            throw new LlmException("The AI service returned an empty response");
        }

        JsonNode usage = response.path("usage");
        JsonNode choice = response.path("choices").path(0);
        log.info("{} usage: inputTokens={}, outputTokens={}, finishReason={}", provider,
                usage.path("prompt_tokens").asInt(-1),
                usage.path("completion_tokens").asInt(-1),
                choice.path("finish_reason").asText("unknown"));

        return choice.path("message").path("content").asText("");
    }
}
