package com.tariffhelper.service;

import com.tariffhelper.domain.CreditedSeniority;
import com.tariffhelper.domain.SalaryStep;
import com.tariffhelper.domain.SeniorityResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Works out how many years the employer already credits, from payslip values. Code only.
 *
 * A lønnstrinn is used when the ladder has step numbers (Oslo): the credited years are the lowest
 * fromYears with that step. If the trinn is not on the ladder, no comparison is made. Otherwise the
 * ansiennitetsdato is used: whole months from that date to now, divided by 12.
 */
@Component
public class CreditedSeniorityCalculator {

    public CreditedSeniority calculate(List<SalaryStep> ladder, Integer lonnstrinn, YearMonth ansiennitetsdato,
                                       YearMonth now) {
        boolean ladderHasSteps = ladder.stream().anyMatch(s -> s.step() != null);
        if (lonnstrinn != null && ladderHasSteps) {
            return ladder.stream()
                    .filter(s -> Objects.equals(s.step(), lonnstrinn))
                    .mapToInt(SalaryStep::fromYears)
                    .min()
                    .stream()
                    .mapToObj(years -> new CreditedSeniority(
                            BigDecimal.valueOf(years).setScale(2, RoundingMode.UNNECESSARY),
                            CreditedSeniority.BASIS_LONNSTRINN, false, false))
                    .findFirst()
                    .orElse(new CreditedSeniority(null, null, true, false));
        }
        boolean stepNumbersMissing = lonnstrinn != null;
        if (ansiennitetsdato != null) {
            int months = (int) ChronoUnit.MONTHS.between(ansiennitetsdato, now);
            return new CreditedSeniority(SeniorityResult.years(Math.max(months, 0)),
                    CreditedSeniority.BASIS_ANSIENNITETSDATO, false, stepNumbersMissing);
        }
        return stepNumbersMissing ? new CreditedSeniority(null, null, false, true) : CreditedSeniority.NONE;
    }
}
