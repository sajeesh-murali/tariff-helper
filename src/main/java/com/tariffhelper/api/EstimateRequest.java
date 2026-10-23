package com.tariffhelper.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * The web form.
 *
 * positionPercent and currentAnnualSalary describe the person's CURRENT job: the salary is what they
 * actually earn per year at that percentage. The application converts it to a full-time figure.
 *
 * agreementId and jobCategory are optional: without agreementId the agreement is suggested from
 * employerName; without jobCategory it is derived from stillingskode or jobTitle.
 * stillingskode, lonnstrinn and ansiennitetsdato are typed in from the payslip (optional). They are used
 * only to compare with the calculated seniority, and are never sent to the LLM.
 */
public record EstimateRequest(
        @Size(max = 40) String agreementId,
        @Size(max = 60) String jobCategory,
        @NotNull @Min(1) @Max(100) Integer positionPercent,
        @NotNull @Positive @Max(5_000_000) BigDecimal currentAnnualSalary,
        @NotNull @Size(max = 30) List<@Valid @NotNull ExperienceDto> experiences,
        @Size(min = 2, max = 100) @Pattern(regexp = FormText.REGEX, message = FormText.MESSAGE) String employerName,
        @Size(max = 60) @Pattern(regexp = FormText.REGEX, message = FormText.MESSAGE) String jobTitle,
        @Pattern(regexp = "^\\d{1,6}$", message = "must be up to 6 digits") String stillingskode,
        @Min(1) @Max(100) Integer lonnstrinn,
        YearMonth ansiennitetsdato) {

    /** The original form without employer or payslip fields. */
    public EstimateRequest(String agreementId, String jobCategory, Integer positionPercent,
                           BigDecimal currentAnnualSalary, List<ExperienceDto> experiences) {
        this(agreementId, jobCategory, positionPercent, currentAnnualSalary, experiences,
                null, null, null, null, null);
    }
}
