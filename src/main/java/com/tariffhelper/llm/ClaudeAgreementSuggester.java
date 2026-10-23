package com.tariffhelper.llm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tariffhelper.domain.ConfidenceLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Call 1 through Claude. The answer is parsed defensively and checked against allowlists; anything
 * unexpected, and any failure, becomes "unknown". This never throws to the caller.
 *
 * Privacy: prompts and model answers are never logged, only which check failed.
 */
public class ClaudeAgreementSuggester implements AgreementSuggester {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAgreementSuggester.class);

    static final int MAX_TOKENS = 200;
    static final int MAX_REASON_CHARS = 200;

    private static final Map<String, ConfidenceLevel> CONFIDENCES = Map.of(
            "high", ConfidenceLevel.HIGH,
            "medium", ConfidenceLevel.MEDIUM,
            "low", ConfidenceLevel.LOW,
            "unknown", ConfidenceLevel.UNKNOWN);
    // The reason is shown to the user, so only plain sentence characters are allowed (no markup).
    private static final Pattern SAFE_REASON = Pattern.compile("^[\\p{L}\\p{N} .,:;!?&()/'\"-]*$");
    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*(.*?)\\s*```$", Pattern.DOTALL);

    private final ClaudeMessagesApi api;
    private final PromptBuilder prompts;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public ClaudeAgreementSuggester(ClaudeMessagesApi api, PromptBuilder prompts) {
        this.api = api;
        this.prompts = prompts;
    }

    @Override
    public AgreementSuggestion suggest(String employerName, String jobTitle, List<String> allowedAgreementIds) {
        String answer;
        try {
            answer = api.send(prompts.suggestSystemPrompt(),
                    prompts.suggestUserPrompt(employerName, jobTitle, allowedAgreementIds), MAX_TOKENS);
        } catch (RuntimeException e) {
            // ClaudeMessagesApi has already logged the status; the user just gets no suggestion.
            log.warn("Agreement suggestion failed, using unknown: {}", e.getClass().getSimpleName());
            return AgreementSuggestion.unknown();
        }
        AgreementSuggestion result = parse(answer, allowedAgreementIds);
        log.info("Agreement suggestion: model={}, accepted={}", api.model(), !result.isUnknown());
        return result;
    }

    AgreementSuggestion parse(String answer, List<String> allowedAgreementIds) {
        if (answer == null) {
            return AgreementSuggestion.unknown();
        }
        String text = answer.strip();
        var fenced = CODE_FENCE.matcher(text);
        if (fenced.matches()) {
            text = fenced.group(1);
        }
        JsonNode json;
        try {
            json = mapper.readTree(text);
        } catch (Exception e) {
            return rejected("not valid JSON");
        }
        if (json == null || !json.isObject()) {
            return rejected("not a JSON object");
        }
        JsonNode id = json.get("agreementId");
        JsonNode confidence = json.get("confidence");
        JsonNode reason = json.get("reason");
        if (id == null || !id.isTextual() || confidence == null || !confidence.isTextual()
                || reason == null || !reason.isTextual()) {
            return rejected("missing or wrong-type field");
        }
        if (!allowedAgreementIds.contains(id.asText())) {
            return rejected("agreement id not in the allowlist");
        }
        ConfidenceLevel level = CONFIDENCES.get(confidence.asText().toLowerCase(Locale.ROOT));
        if (level == null || level == ConfidenceLevel.UNKNOWN) {
            return rejected("confidence not usable");
        }
        String reasonText = reason.asText().strip();
        if (reasonText.length() > MAX_REASON_CHARS || !SAFE_REASON.matcher(reasonText).matches()) {
            return rejected("reason too long or has unsafe characters");
        }
        return new AgreementSuggestion(id.asText(), level, reasonText);
    }

    private static AgreementSuggestion rejected(String check) {
        log.warn("Agreement suggestion rejected: {}", check);
        return AgreementSuggestion.unknown();
    }
}
