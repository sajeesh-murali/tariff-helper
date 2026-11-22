package com.tariffhelper.domain;

/**
 * What one work-history entry contributes.
 *
 * @param countedMonths       months that count towards seniority
 * @param possibleExtraMonths further months that count only if the employer accepts them
 * @param note                why, in plain English (fixed text from the rules, not model output)
 * @param enteredMonths       the length the person entered for this period, before overlap, limits and the rules
 *                            (the total of these is shown as "experience entered"; it does not decide pay)
 * @param label               short Norwegian name of the period for display, or null
 */
public record PerExperience(int index, int countedMonths, int possibleExtraMonths, String note,
                            int enteredMonths, String label) {

    /** For entries without a known length or name: the entered months are the months that count or may count. */
    public PerExperience(int index, int countedMonths, int possibleExtraMonths, String note) {
        this(index, countedMonths, possibleExtraMonths, note, countedMonths + possibleExtraMonths, null);
    }
}
