package com.tariffhelper.domain;

/**
 * An agreement id suggested for an employer, with how sure the code (or the AI) is.
 *
 * @param reason plain-English reason the user may see
 */
public record AgreementMatch(String agreementId, ConfidenceLevel confidence, String reason) {
}
