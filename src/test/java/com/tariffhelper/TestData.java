package com.tariffhelper;

import com.tariffhelper.api.dto.request.EstimateRequest;
import com.tariffhelper.api.dto.request.ExperienceDto;
import com.tariffhelper.domain.Agreement;
import com.tariffhelper.domain.ExperienceKind;
import com.tariffhelper.domain.JobCategory;
import com.tariffhelper.domain.Relevance;
import com.tariffhelper.domain.Rules;
import com.tariffhelper.domain.SalaryStep;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Small fixtures shared by the tests. The pay ladders mirror the KS tables from 1 May 2026. */
public final class TestData {

    public static final JobCategory NURSE =
            new JobCategory("nurse", "Nurse (sykepleier)", true, "201", null);
    public static final JobCategory ASSISTANT =
            new JobCategory("kindergarten_assistant", "Kindergarten assistant", false, "101", "Assumed pay group 101.");
    public static final Rules RULES = Rules.of(3, 6);

    public static final List<SalaryStep> LADDER_201 = List.of(
            step(0, 545_400), step(6, 558_400), step(8, 568_600), step(10, 620_800), step(16, 639_900));
    public static final List<SalaryStep> LADDER_101 = List.of(
            step(0, 418_600), step(6, 421_600), step(8, 429_700), step(10, 476_000), step(16, 515_800));

    private TestData() {
    }

    public static SalaryStep step(int fromYears, int annualSalary) {
        return new SalaryStep(fromYears, BigDecimal.valueOf(annualSalary));
    }

    public static ExperienceDto exp(ExperienceKind kind, Relevance relevance, String start, String end) {
        return new ExperienceDto("NO", kind, relevance, null, YearMonth.parse(start), YearMonth.parse(end), null);
    }

    public static Agreement agreement() {
        return new Agreement("ks-hta", "KS Hovedtariffavtale (municipalities)", "municipal", "available",
                "2026-05-01", List.of(NURSE, ASSISTANT), RULES,
                Map.of("201", LADDER_201, "101", LADDER_101),
                "Rules summary text.", List.of("Caveat one."), null);
    }

    public static EstimateRequest request(int currentAnnualSalary, List<ExperienceDto> experiences) {
        return new EstimateRequest("ks-hta", "nurse", 100, BigDecimal.valueOf(currentAnnualSalary), experiences);
    }
}
