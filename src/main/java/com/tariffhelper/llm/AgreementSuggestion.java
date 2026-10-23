package com.tariffhelper.llm;

import com.tariffhelper.domain.ConfidenceLevel;

/**
 * Call 1 result after validation: an agreement id from the allowlist, or "unknown".
 * The reason is short, checked text the user may see.
 */
public record AgreementSuggestion(String agreementId, ConfidenceLevel confidence, String reason) {

    public static final String UNKNOWN_ID = "unknown";

    public static AgreementSuggestion unknown() {
        return new AgreementSuggestion(UNKNOWN_ID, ConfidenceLevel.UNKNOWN, null);
    }

    public boolean isUnknown() {
        return UNKNOWN_ID.equals(agreementId) || confidence == ConfidenceLevel.UNKNOWN;
    }
}
