package com.tariffhelper.service;

import com.tariffhelper.domain.ExperienceKind;
import com.tariffhelper.domain.HomeWorkRule;
import com.tariffhelper.domain.PrivateServiceRule;
import com.tariffhelper.domain.Relevance;
import com.tariffhelper.domain.Rules;
import com.tariffhelper.domain.SeniorityResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tariffhelper.TestData.ASSISTANT;
import static com.tariffhelper.TestData.NURSE;
import static com.tariffhelper.TestData.exp;
import static org.assertj.core.api.Assertions.assertThat;

/** Oslo kommune: all fixed-hours service counts, care work up to 2 years, work in the home is only possible. */
class SeniorityCalculatorOsloTest {

    private static final Rules OSLO = new Rules(2, 2, PrivateServiceRule.ALWAYS_FULL, HomeWorkRule.POSSIBLE_ONLY);

    private final SeniorityCalculator calculator = new SeniorityCalculator();

    @Test
    void unrelatedPrivateServiceCountsInFullEvenForANurse() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.PRIVATE_SERVICE, Relevance.UNRELATED, "2010-01", "2021-12")), NURSE, OSLO);
        assertThat(r.countedMonths()).isEqualTo(144);
        assertThat(r.possibleExtraMonths()).isZero();
        assertThat(r.perExperience().get(0).note()).contains("whether or not it is related");
    }

    @Test
    void privateServiceWithoutRelevanceAnswerCountsInFull() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.PRIVATE_SERVICE, null, "2019-01", "2019-12")), NURSE, OSLO);
        assertThat(r.countedMonths()).isEqualTo(12);
    }

    @Test
    void careWorkCountsOnlyUpToTwoYears() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.CARE_WORK, null, "2010-01", "2014-12")), NURSE, OSLO);
        assertThat(r.countedMonths()).isEqualTo(24);
        assertThat(r.perExperience().get(0).note()).contains("limit was reached").contains("36 month");
    }

    @Test
    void workInTheHomeIsNeverCertain() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.HOME_WORK, null, "2010-01", "2010-12")), NURSE, OSLO);
        assertThat(r.countedMonths()).isZero();
        assertThat(r.possibleExtraMonths()).isEqualTo(12);
        assertThat(r.perExperience().get(0).note()).contains("not mentioned").contains("employer decides");
    }

    @Test
    void workInTheHomeIsCappedAtTwoYearsAsPossible() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.HOME_WORK, null, "2000-01", "2009-12")), NURSE, OSLO);
        assertThat(r.countedMonths()).isZero();
        assertThat(r.possibleExtraMonths()).isEqualTo(24);
    }

    @Test
    void workInTheHomeSharesTheTwoYearLimitWithCareWorkThatCounts() {
        // 15 months of care count; only 9 months of the 2-year limit are left for work in the home.
        SeniorityResult r = calculator.calculate(List.of(
                exp(ExperienceKind.HOME_WORK, null, "2000-01", "2004-12"),
                exp(ExperienceKind.CARE_WORK, null, "2010-01", "2011-03")), NURSE, OSLO);
        assertThat(r.countedMonths()).isEqualTo(15);
        assertThat(r.possibleExtraMonths()).isEqualTo(9);
    }

    @Test
    void jobWithoutEducationRequirementGetsTheSameTreatmentInOslo() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.HOME_WORK, null, "2010-01", "2010-12")), ASSISTANT, OSLO);
        assertThat(r.countedMonths()).isZero();
        assertThat(r.possibleExtraMonths()).isEqualTo(12);
    }

    @Test
    void anOverlappingMonthIsStillCountedOnce() {
        SeniorityResult r = calculator.calculate(List.of(
                exp(ExperienceKind.PRIVATE_SERVICE, null, "2018-01", "2018-12"),
                exp(ExperienceKind.PUBLIC_SERVICE, null, "2018-07", "2019-06")), NURSE, OSLO);
        assertThat(r.countedMonths()).isEqualTo(18);
    }
}
