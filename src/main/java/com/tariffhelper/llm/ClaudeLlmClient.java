package com.tariffhelper.llm;

import com.tariffhelper.api.EstimateRequest;
import com.tariffhelper.domain.EstimateFacts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calls the Anthropic Messages API to write the explanation. All numbers are calculated by the
 * application beforehand and handed to the model in the prompt.
 *
 * Privacy: prompts and model answers are never logged. Only status codes, timings and
 * token counts are.
 */
public class ClaudeLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLlmClient.class);

    private static final int MAX_ATTEMPTS = 2; // one retry if the answer comes back empty
    private static final int MAX_EXPLANATION_CHARS = 4000;

    private final ClaudeMessagesApi api;
    private final ClaudeProperties props;
    private final PromptBuilder prompts;

    public ClaudeLlmClient(ClaudeMessagesApi api, ClaudeProperties props, PromptBuilder prompts) {
        this.api = api;
        this.props = props;
        this.prompts = prompts;
    }

    @Override
    public LlmAssessment assess(EstimateRequest request, EstimateFacts facts) {
        String system = prompts.systemPrompt();
        String user = prompts.userPrompt(request, facts);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long start = System.nanoTime();
            String answer = api.send(system, user, props.maxTokens()).strip();
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

    private static String truncate(String text) {
        return text.length() <= MAX_EXPLANATION_CHARS ? text : text.substring(0, MAX_EXPLANATION_CHARS);
    }
}
