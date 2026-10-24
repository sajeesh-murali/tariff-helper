package com.tariffhelper.service;

import com.tariffhelper.api.dto.request.ExperienceDto;
import com.tariffhelper.domain.ExperienceKind;
import com.tariffhelper.domain.Relevance;
import com.tariffhelper.domain.SeniorityResult;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static com.tariffhelper.TestData.ASSISTANT;
import static com.tariffhelper.TestData.NURSE;
import static com.tariffhelper.TestData.RULES;
import static com.tariffhelper.TestData.exp;
import static org.assertj.core.api.Assertions.assertThat;

class SeniorityCalculatorTest {

    private final SeniorityCalculator calculator = new SeniorityCalculator();

    @Test
    void publicServiceCountsInFull() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.PUBLIC_SERVICE, null, "2018-03", "2020-06")), NURSE, RULES);
        assertThat(r.countedMonths()).isEqualTo(28);
        assertThat(r.possibleExtraMonths()).isZero();
        assertThat(r.countedYears()).isEqualByComparingTo("2.33");
    }

    @Test
    void partTimeCountsAsFullTimeForThePeriod() {
        ExperienceDto fifteenPercent = new ExperienceDto("NO", ExperienceKind.PUBLIC_SERVICE, null, null,
                YearMonth.of(2019, 1), YearMonth.of(2019, 12), 15);
        SeniorityResult r = calculator.calculate(List.of(fifteenPercent), NURSE, RULES);
        assertThat(r.countedMonths()).isEqualTo(12);
    }

    @Test
    void relatedPrivateServiceCountsForAJobWithEducationRequirement() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.PRIVATE_SERVICE, Relevance.RELATED, "2019-01", "2019-12")), NURSE, RULES);
        assertThat(r.countedMonths()).isEqualTo(12);
    }

    @Test
    void unrelatedPrivateServiceDoesNotCountForAJobWithEducationRequirement() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.PRIVATE_SERVICE, Relevance.UNRELATED, "2019-01", "2019-12")), NURSE, RULES);
        assertThat(r.countedMonths()).isZero();
        assertThat(r.possibleExtraMonths()).isZero();
        assertThat(r.perExperience().get(0).note()).contains("Does not count");
    }

    @Test
    void partlyRelatedPrivateServiceIsOnlyPossible() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.PRIVATE_SERVICE, Relevance.PARTLY_RELATED, "2019-01", "2019-12")),
                NURSE, RULES);
        assertThat(r.countedMonths()).isZero();
        assertThat(r.possibleExtraMonths()).isEqualTo(12);
        assertThat(r.countedYearsIfAllAccepted()).isEqualByComparingTo("1.00");
    }

    @Test
    void anyPrivateServiceCountsForAJobWithoutEducationRequirement() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.PRIVATE_SERVICE, Relevance.UNRELATED, "2019-01", "2019-12")),
                ASSISTANT, RULES);
        assertThat(r.countedMonths()).isEqualTo(12);
    }

    @Test
    void careWorkIsCappedAtThreeYears() {
        // 60 months of care work, limit is 36
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.CARE_WORK, null, "2010-01", "2014-12")), NURSE, RULES);
        assertThat(r.countedMonths()).isEqualTo(36);
        assertThat(r.perExperience().get(0).note()).contains("limit");
    }

    @Test
    void homeWorkDoesNotCountForAJobWithEducationRequirement() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.HOME_WORK, null, "2010-01", "2011-12")), NURSE, RULES);
        assertThat(r.countedMonths()).isZero();
        assertThat(r.perExperience().get(0).note()).contains("Does not count");
    }

    @Test
    void homeWorkIsCappedAtSixYearsForAJobWithoutEducationRequirement() {
        // 120 months of home work, limit is 72
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.HOME_WORK, null, "2000-01", "2009-12")), ASSISTANT, RULES);
        assertThat(r.countedMonths()).isEqualTo(72);
    }

    @Test
    void overlappingPeriodsAreCountedOnce() {
        // Jan-Dec 2018 and Jul 2018-Jun 2019 overlap for 6 months: 12 + 12 - 6 = 18
        SeniorityResult r = calculator.calculate(List.of(
                exp(ExperienceKind.PUBLIC_SERVICE, null, "2018-01", "2018-12"),
                exp(ExperienceKind.PUBLIC_SERVICE, null, "2018-07", "2019-06")), NURSE, RULES);
        assertThat(r.countedMonths()).isEqualTo(18);
        assertThat(r.perExperience().get(1).countedMonths()).isEqualTo(6);
        assertThat(r.perExperience().get(1).note()).contains("overlap");
    }

    @Test
    void paidWorkTakesPriorityOverCareWorkInTheSameMonths() {
        // care Jan-Dec 2018, public service Jan-Jun 2018: months 1-6 go to public service, 7-12 to care
        SeniorityResult r = calculator.calculate(List.of(
                exp(ExperienceKind.CARE_WORK, null, "2018-01", "2018-12"),
                exp(ExperienceKind.PUBLIC_SERVICE, null, "2018-01", "2018-06")), NURSE, RULES);
        assertThat(r.perExperience().get(0).countedMonths()).isEqualTo(6);
        assertThat(r.perExperience().get(1).countedMonths()).isEqualTo(6);
        assertThat(r.countedMonths()).isEqualTo(12);
    }

    @Test
    void unrelatedPaidWorkStillBlocksCareWorkInTheSameMonths() {
        SeniorityResult r = calculator.calculate(List.of(
                exp(ExperienceKind.CARE_WORK, null, "2018-01", "2018-12"),
                exp(ExperienceKind.PRIVATE_SERVICE, Relevance.UNRELATED, "2018-01", "2018-12")), NURSE, RULES);
        assertThat(r.countedMonths()).isZero();
    }

    @Test
    void selfEmploymentIsOnlyPossible() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.SELF_EMPLOYED, null, "2015-01", "2015-12")), ASSISTANT, RULES);
        assertThat(r.countedMonths()).isZero();
        assertThat(r.possibleExtraMonths()).isEqualTo(12);
    }

    @Test
    void militaryServiceCountsInFull() {
        SeniorityResult r = calculator.calculate(
                List.of(exp(ExperienceKind.MILITARY_SERVICE, null, "2005-08", "2006-07")), NURSE, RULES);
        assertThat(r.countedMonths()).isEqualTo(12);
    }

    @Test
    void emptyHistoryGivesZero() {
        SeniorityResult r = calculator.calculate(List.of(), NURSE, RULES);
        assertThat(r.countedMonths()).isZero();
        assertThat(r.possibleExtraMonths()).isZero();
        assertThat(r.perExperience()).isEmpty();
    }

    @Test
    void mixedHistoryForAJobWithoutEducationRequirementAddsUp() {
        // private 60 + home 36 + care 12 + public 24 months, no overlaps
        SeniorityResult r = calculator.calculate(List.of(
                exp(ExperienceKind.PRIVATE_SERVICE, Relevance.UNRELATED, "2000-01", "2004-12"),
                exp(ExperienceKind.HOME_WORK, null, "2005-01", "2007-12"),
                exp(ExperienceKind.CARE_WORK, null, "2008-01", "2008-12"),
                exp(ExperienceKind.PUBLIC_SERVICE, null, "2009-01", "2010-12")), ASSISTANT, RULES);
        assertThat(r.countedMonths()).isEqualTo(132);
        assertThat(r.countedYears()).isEqualByComparingTo("11.00");
    }
}
