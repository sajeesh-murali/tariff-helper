package com.tariffhelper.api.dto.response;

import com.tariffhelper.domain.ConfidenceLevel;
import com.tariffhelper.domain.SuggestionSource;

import java.util.List;

/**
 * Suggested agreements, best first. Empty when nothing matched.
 *
 * @param source "rules" (code), "ai" (Claude, validated against the allowlist) or "none"
 */
public record SuggestResponse(List<Suggestion> suggestions, SuggestionSource source) {

    /** @param jobCategory set only when the job title matches one of this agreement's job categories */
    public record Suggestion(String agreementId, ConfidenceLevel confidence, String reason, String jobCategory) {
    }

    public static SuggestResponse none() {
        return new SuggestResponse(List.of(), SuggestionSource.NONE);
    }
}
