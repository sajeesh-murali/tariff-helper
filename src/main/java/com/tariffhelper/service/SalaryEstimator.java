package com.tariffhelper.service;

import com.tariffhelper.domain.SalaryRange;
import com.tariffhelper.domain.SalaryStep;
import com.tariffhelper.domain.SeniorityResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Looks up the agreement's minimum pay for a given seniority. */
@Component
public class SalaryEstimator {

    /** Minimum pay after the given number of months. A step applies from its completed year. */
    public BigDecimal minimumSalary(List<SalaryStep> ladder, int months) {
        int completedYears = months / 12;
        BigDecimal result = null;
        int bestFromYears = -1;
        for (SalaryStep step : ladder) {
            if (step.fromYears() <= completedYears && step.fromYears() > bestFromYears) {
                result = step.annualSalary();
                bestFromYears = step.fromYears();
            }
        }
        if (result == null) {
            throw new IllegalStateException("The salary ladder has no step for this seniority");
        }
        return result;
    }

    /** From "only what counts" (min) to "if every uncertain period is accepted" (max). */
    public SalaryRange rangeFor(List<SalaryStep> ladder, SeniorityResult seniority) {
        return new SalaryRange(
                minimumSalary(ladder, seniority.countedMonths()),
                minimumSalary(ladder, seniority.countedMonths() + seniority.possibleExtraMonths()));
    }
}
