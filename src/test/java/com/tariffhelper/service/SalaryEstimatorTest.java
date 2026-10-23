package com.tariffhelper.service;

import com.tariffhelper.domain.SalaryRange;
import com.tariffhelper.domain.SalaryStep;
import com.tariffhelper.domain.SeniorityResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.tariffhelper.TestData.LADDER_201;
import static com.tariffhelper.TestData.step;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalaryEstimatorTest {

    private final SalaryEstimator estimator = new SalaryEstimator();

    @Test
    void payStepsUpOnlyAtTheSixEightTenAndSixteenYearMarks() {
        assertStep(0, 545_400);
        assertStep(71, 545_400);   // 5 years 11 months: still the first step
        assertStep(72, 558_400);   // 6 years
        assertStep(95, 558_400);
        assertStep(96, 568_600);   // 8 years
        assertStep(119, 568_600);
        assertStep(120, 620_800);  // 10 years
        assertStep(191, 620_800);
        assertStep(192, 639_900);  // 16 years
        assertStep(600, 639_900);  // far beyond the last step
    }

    @Test
    void rangeRunsFromCountedToIfAllAccepted() {
        // 5 years counted (first step), 5 more years possible (10 years in total: 620 800)
        SeniorityResult seniority = new SeniorityResult(60, 60, List.of());
        SalaryRange range = estimator.rangeFor(LADDER_201, seniority);
        assertThat(range.min()).isEqualByComparingTo("545400");
        assertThat(range.max()).isEqualByComparingTo("620800");
    }

    @Test
    void ladderOrderInTheFileDoesNotMatter() {
        List<SalaryStep> reversed = new ArrayList<>(LADDER_201);
        Collections.reverse(reversed);
        assertThat(estimator.minimumSalary(reversed, 130)).isEqualByComparingTo("620800");
    }

    @Test
    void ladderWithoutAStepForTheSeniorityIsAnError() {
        List<SalaryStep> startsAtFive = List.of(step(5, 500_000));
        assertThatThrownBy(() -> estimator.minimumSalary(startsAtFive, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    private void assertStep(int months, int expected) {
        assertThat(estimator.minimumSalary(LADDER_201, months))
                .as("after %d months", months)
                .isEqualByComparingTo(String.valueOf(expected));
    }
}
