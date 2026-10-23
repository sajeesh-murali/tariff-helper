package com.tariffhelper.api;

import com.tariffhelper.domain.PerExperience;
import com.tariffhelper.domain.SalaryRange;

import java.math.BigDecimal;
import java.util.List;

/**
 * All salary figures are annual, for a full-time (100%) position.
 * The ranges run from "only what counts" (min) to "if the employer accepts everything uncertain" (max).
 */
public record EstimateResponse(
        String agreementApplied,
        String rulesValidFrom,
        String jobCategory,
        String salaryGroup,
        BigDecimal currentFullTimeSalary,
        BigDecimal countedYears,
        BigDecimal countedYearsIfAllAccepted,
        SalaryRange estimatedMinimumSalary,
        SalaryRange differenceFromCurrent,
        List<PerExperience> perExperience,
        String explanation,
        List<String> caveats,
        String disclaimer) {
}
