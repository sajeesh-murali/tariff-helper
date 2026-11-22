package com.tariffhelper.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Outcome of the seniority calculation, as a range:
 * the months that count, and extra months that count only if the employer accepts them.
 */
public record SeniorityResult(int countedMonths, int possibleExtraMonths, List<PerExperience> perExperience) {

    public BigDecimal countedYears() {
        return years(countedMonths);
    }

    public BigDecimal countedYearsIfAllAccepted() {
        return years(countedMonths + possibleExtraMonths);
    }

    /** Everything the person entered, added up: overlap is not removed and no period is left out. Not used for pay. */
    public int enteredMonths() {
        return perExperience.stream().mapToInt(PerExperience::enteredMonths).sum();
    }

    public BigDecimal enteredYears() {
        return years(enteredMonths());
    }

    public static BigDecimal years(int months) {
        return BigDecimal.valueOf(months).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }
}
