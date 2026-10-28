package com.tariffhelper.api.dto.request;

import com.tariffhelper.domain.ExperienceKind;
import com.tariffhelper.domain.PeriodOverlap;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * The questions asked before the estimate, answered as total time (years and months) without dates. Every answer is
 * optional; an answer that is missing or has no time is ignored. Part-time work is stated as the time the person was
 * employed, not converted to full time.
 *
 * It sits beside the dated list of periods: periods with exact dates are better, because overlap can then be
 * removed exactly. periodsOverlap says whether any of the periods happened at the same time; when it is missing and
 * there is more than one period, it is treated as UNSURE.
 *
 * @param workInNorway    work in Norway: current job, earlier jobs, part-time jobs and own firm; not au pair or military
 * @param workAbroad      the same, outside Norway
 * @param auPair          au pair stays, in any country
 * @param militaryService military or civil service, in Norway or another country
 * @param childCare       time at home caring for the person's own children
 * @param otherHome       other periods of care or work in the home, not already stated as child care
 */
public record ScreeningDto(
        @Valid WorkAnswer workInNorway,
        @Valid WorkAnswer workAbroad,
        @Valid PeriodAnswer auPair,
        @Valid MilitaryAnswer militaryService,
        @Valid ChildCareAnswer childCare,
        @Valid OtherHomeAnswer otherHome,
        PeriodOverlap periodsOverlap) {

    /** Years and months, for example 2 years and 3 months. Both parts are optional. */
    public record Duration(@Min(0) @Max(60) Integer years, @Min(0) @Max(11) Integer months) {

        public int totalMonths() {
            return (years == null ? 0 : years) * 12 + (months == null ? 0 : months);
        }
    }

    /** @param selfEmployed the part of the total that was own firm or self-employment (judged separately) */
    public record WorkAnswer(@Valid Duration duration, @Valid Duration selfEmployed, Boolean approximate) {
    }

    public record PeriodAnswer(@Valid Duration duration, Boolean approximate) {
    }

    /** @param country 2-letter code of the country of service; it is only mentioned in the result */
    public record MilitaryAnswer(@Valid Duration duration,
                                 @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a 2-letter country code")
                                 String country,
                                 Boolean approximate) {
    }

    /** @param children how many children; it does not change the result, only the time at home does */
    public record ChildCareAnswer(@Min(0) @Max(20) Integer children, @Valid Duration duration, Boolean approximate) {
    }

    /** @param type care_work (children, elderly or sick people) or home_work (other work in the home) */
    public record OtherHomeAnswer(ExperienceKind type, @Valid Duration duration, Boolean approximate) {
    }

    public static int months(Duration duration) {
        return duration == null ? 0 : duration.totalMonths();
    }
}
