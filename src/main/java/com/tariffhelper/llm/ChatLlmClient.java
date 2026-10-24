package com.tariffhelper.llm;

import com.tariffhelper.api.EstimateRequest;
import com.tariffhelper.domain.EstimateFacts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calls the configured model (Llama, OpenAI or Anthropic) to write the explanation. All numbers are calculated by the
 * application beforehand and handed to the model in the prompt.
 *
 * Privacy: prompts and model answers are never logged. Only status codes, timings and
 * token counts are.
 */
public class ChatLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ChatLlmClient.class);

    private static final int MAX_ATTEMPTS = 2; // one retry if the answer comes back empty
    private static final int MAX_EXPLANATION_CHARS = 4000;

    private final ChatApi api;
    private final PromptBuilder prompts;

    public ChatLlmClient(ChatApi api, PromptBuilder prompts) {
        this.api = api;
        this.prompts = prompts;
    }

    @Override
    public LlmAssessment assess(EstimateRequest request, EstimateFacts facts) {
        String system = prompts.systemPrompt();
        String user = prompts.userPrompt(request, facts);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long start = System.nanoTime();
            String answer = api.send(system, user, api.maxTokens()).strip();
            long millis = (System.nanoTime() - start) / 1_000_000;
            if (!answer.isEmpty()) {
                log.info("LLM explanation accepted: model={}, attempt={}, durationMs={}",
                        api.model(), attempt, millis);
                return new LlmAssessment(truncate(answer));
            }
            log.warn("LLM returned an empty answer: attempt={}, durationMs={}", attempt, millis);
        }
        throw new LlmException("The AI service did not return a usable answer");
    }

    private static String truncate(String text) {
        return text.length() <= MAX_EXPLANATION_CHARS ? text : text.substring(0, MAX_EXPLANATION_CHARS);
    }
}
