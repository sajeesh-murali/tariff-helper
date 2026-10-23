package com.tariffhelper.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.tariffhelper.api.EstimateRequest;
import com.tariffhelper.domain.EstimateFacts;
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
 * Calls the Anthropic Messages API to write the explanation. All numbers are calculated by the
 * application beforehand and handed to the model in the prompt.
 *
 * Privacy: prompts and model answers are never logged. Only status codes, timings and
 * token counts are.
 */
public class ClaudeLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLlmClient.class);

    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_ATTEMPTS = 2; // one retry if the answer comes back empty
    private static final int MAX_EXPLANATION_CHARS = 4000;

    private final RestClient restClient;
    private final ClaudeProperties props;
    private final PromptBuilder prompts;

    public ClaudeLlmClient(RestClient restClient, ClaudeProperties props, PromptBuilder prompts) {
        this.restClient = restClient;
        this.props = props;
        this.prompts = prompts;
    }

    @Override
    public LlmAssessment assess(EstimateRequest request, EstimateFacts facts) {
        String system = prompts.systemPrompt();
        String user = prompts.userPrompt(request, facts);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long start = System.nanoTime();
            String answer = callClaude(system, user).strip();
            long millis = (System.nanoTime() - start) / 1_000_000;
            if (!answer.isEmpty()) {
                log.info("Claude explanation accepted: model={}, attempt={}, durationMs={}",
                        props.model(), attempt, millis);
                return new LlmAssessment(truncate(answer));
            }
            log.warn("Claude returned an empty answer: attempt={}, durationMs={}", attempt, millis);
        }
        throw new LlmException("The AI service did not return a usable answer");
    }

    private String callClaude(String system, String user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", props.model());
        payload.put("max_tokens", props.maxTokens());
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

    private static String truncate(String text) {
        return text.length() <= MAX_EXPLANATION_CHARS ? text : text.substring(0, MAX_EXPLANATION_CHARS);
    }
}
