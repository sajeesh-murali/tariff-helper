package com.tariffhelper.api.dto.request;
import com.tariffhelper.api.FormText;

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
 * Both are optional. A person without a fixed position (for example a tilkallingsvikar, 0% or no percentage) or
 * without a known salary gets the agreement's full-time pay for the counted seniority, with no comparison.
 *
 * agreementId and jobCategory are optional: without agreementId the agreement is suggested from
 * employerName; without jobCategory it is derived from stillingskode or jobTitle.
 * stillingskode, lonnstrinn and ansiennitetsdato come from the payslip (optional), typed in or filled from
 * POST /api/payslips/parse after the person checked them. They are used only to compare with the calculated
 * seniority, and are never sen     t to the LLM by this endpoint.
 */
public record EstimateRequest(
        @Size(max = 40) String agreementId,
        @Size(max = 60) String jobCategory,
        @Min(0) @Max(100) Integer positionPercent,
        @Positive @Max(5_000_000) BigDecimal currentAnnualSalary,
        @NotNull @Size(max = 30) List<@Valid @NotNull ExperienceDto> experiences,
        @Size(min = 2, max = 100) @Pattern(regexp = FormText.REGEX, message = FormText.MESSAGE) String employerName,
        @Size(max = 60) @Pattern(regexp = FormText.REGEX, message = FormText.MESSAGE) String jobTitle,
        @Pattern(regexp = "^\\d{1,7}$", message = "must be up to 7 digits") String stillingskode,
        @Min(1) @Max(100) Integer lonnstrinn,
        YearMonth ansiennitetsdato,
        // Optional answers to the questions asked before the estimate, as total time without dates.
        @Valid ScreeningDto screening) {

    /** The form without the screening answers. */
    public EstimateRequest(String agreementId, String jobCategory, Integer positionPercent,
                           BigDecimal currentAnnualSalary, List<ExperienceDto> experiences,
                           String employerName, String jobTitle, String stillingskode, Integer lonnstrinn,
                           YearMonth ansiennitetsdato) {
        this(agreementId, jobCategory, positionPercent, currentAnnualSalary, experiences,
                employerName, jobTitle, stillingskode, lonnstrinn, ansiennitetsdato, null);
    }

    /** The original form without employer, payslip or screening fields. */
    public EstimateRequest(String agreementId, String jobCategory, Integer positionPercent,
                           BigDecimal currentAnnualSalary, List<ExperienceDto> experiences) {
        this(agreementId, jobCategory, positionPercent, currentAnnualSalary, experiences,
                null, null, null, null, null, null);
    }
}
