package com.tariffhelper.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tariffhelper.domain.ExperienceKind;
import com.tariffhelper.domain.Relevance;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.YearMonth;

/**
 * One period in the person's history.
 *
 * relevance is only needed for private service in a job that requires education.
 * positionPercent is optional: municipalities count part-time as full time for the period,
 * so it does not change the result. workType is optional context.
 */
public record ExperienceDto(
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a 2-letter country code") String country,
        @NotNull ExperienceKind kind,
        Relevance relevance,
        // Restricted to a safe character set because this value is placed in the LLM prompt.
        @Pattern(regexp = "^[a-z0-9_]{1,60}$",
                message = "must be lowercase letters, digits or underscores") String workType,
        @NotNull YearMonth startDate,
        @NotNull YearMonth endDate,
        @Min(1) @Max(100) Integer positionPercent) {

    @JsonIgnore
    @AssertTrue(message = "endDate must not be before startDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
