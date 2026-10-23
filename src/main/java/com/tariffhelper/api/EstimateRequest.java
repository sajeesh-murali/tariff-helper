package com.tariffhelper.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * The web form.
 *
 * positionPercent and currentAnnualSalary describe the person's CURRENT job: the salary is what they
 * actually earn per year at that percentage. The application converts it to a full-time figure.
 */
public record EstimateRequest(
        @NotBlank @Size(max = 40) String agreementId,
        @NotBlank @Size(max = 60) String jobCategory,
        @NotNull @Min(1) @Max(100) Integer positionPercent,
        @NotNull @Positive @Max(5_000_000) BigDecimal currentAnnualSalary,
        @NotNull @Size(max = 30) List<@Valid @NotNull ExperienceDto> experiences) {
}
