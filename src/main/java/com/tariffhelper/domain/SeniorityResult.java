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

    public static BigDecimal years(int months) {
        return BigDecimal.valueOf(months).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }
}
