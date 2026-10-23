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
 * One call to the Anthropic Messages API, shared by every Claude-backed client.
 *
 * Privacy: prompts and model answers are never logged. Only status codes and token counts are.
 */
public class ClaudeMessagesApi {

    private static final Logger log = LoggerFactory.getLogger(ClaudeMessagesApi.class);

    private static final String API_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final ClaudeProperties props;

    public ClaudeMessagesApi(RestClient restClient, ClaudeProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    public String model() {
        return props.model();
    }

    /** Sends one user message and returns the joined text blocks of the answer (may be empty). */
    public String send(String system, String user, int maxTokens) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", props.model());
        payload.put("max_tokens", maxTokens);
        payload.put("system", system);
        payload.put("messages", List.of(Map.of("role", "user", "content", user)));

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", props.apiKey())
                    .header("anthropic-version", API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.error("Claude API returned an error: status={}", status);
            throw new LlmException("The AI service returned an error (HTTP " + status + ")");
        } catch (RestClientException e) {
            log.error("Claude API call failed: {}", e.getClass().getSimpleName());
            throw new LlmException("Could not reach the AI service");
        }

        if (response == null) {
            throw new LlmException("The AI service returned an empty response");
        }

        JsonNode usage = response.path("usage");
        log.info("Claude usage: inputTokens={}, outputTokens={}, stopReason={}",
                usage.path("input_tokens").asInt(-1),
                usage.path("output_tokens").asInt(-1),
                response.path("stop_reason").asText("unknown"));

        StringBuilder text = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        return text.toString();
    }
}
