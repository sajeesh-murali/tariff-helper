package com.tariffhelper.domain;

/**
 * What one work-history entry contributes.
 *
 * @param countedMonths       months that count towards seniority
 * @param possibleExtraMonths further months that count only if the employer accepts them
 * @param note                why, in plain English (fixed text from the rules, not model output)
 */
public record PerExperience(int index, int countedMonths, int possibleExtraMonths, String note) {
}
