package com.tariffhelper.service;

import com.tariffhelper.api.dto.request.ScreeningDto;
import com.tariffhelper.api.dto.request.ScreeningDto.ChildCareAnswer;
import com.tariffhelper.api.dto.request.ScreeningDto.Duration;
import com.tariffhelper.api.dto.request.ScreeningDto.MilitaryAnswer;
import com.tariffhelper.api.dto.request.ScreeningDto.OtherHomeAnswer;
import com.tariffhelper.api.dto.request.ScreeningDto.PeriodAnswer;
import com.tariffhelper.api.dto.request.ScreeningDto.WorkAnswer;
import com.tariffhelper.domain.ExperienceKind;
import com.tariffhelper.domain.PerExperience;
import com.tariffhelper.domain.PeriodOverlap;
import com.tariffhelper.domain.PrivateServiceRule;
import com.tariffhelper.domain.Rules;
import com.tariffhelper.domain.HomeWorkRule;
import com.tariffhelper.domain.SeniorityResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tariffhelper.TestData.ASSISTANT;
import static com.tariffhelper.TestData.NURSE;
import static com.tariffhelper.TestData.RULES;
import static org.assertj.core.api.Assertions.assertThat;

/** The questionnaire answers go through the same agreement rules as dated periods; only overlap is new. */
class ScreeningSeniorityCalculatorTest {

    /** Oslo kommune rules: all service counts, care limit 2 years, work in the home only uncertain. */
    private static final Rules OSLO = new Rules(2, 2, PrivateServiceRule.ALWAYS_FULL, HomeWorkRule.POSSIBLE_ONLY);

    private static final SeniorityResult NO_DATED = new SeniorityResult(0, 0, List.of());

    private final ScreeningSeniorityCalculator calculator =
            new ScreeningSeniorityCalculator(new SeniorityCalculator());

    private static Duration months(int total) {
        return new Duration(total / 12, total % 12);
    }

    private static WorkAnswer work(int total) {
        return new WorkAnswer(months(total), null, null);
    }

    private static ScreeningDto screening(WorkAnswer norway, WorkAnswer abroad, PeriodAnswer auPair,
                                          MilitaryAnswer military, ChildCareAnswer childCare,
                                          OtherHomeAnswer otherHome, PeriodOverlap overlap) {
        return new ScreeningDto(norway, abroad, auPair, military, childCare, otherHome, overlap);
    }

    private static ScreeningDto onlyWork(int total) {
        return screening(work(total), null, null, null, null, null, null);
    }

    private SeniorityResult run(ScreeningDto s) {
        return calculator.combine(NO_DATED, 0, s, ASSISTANT, RULES);
    }

    @Test
    void workCountsInFullInAPositionWithoutAnEducationRequirement() {
        SeniorityResult result = run(onlyWork(36));

        assertThat(result.countedMonths()).isEqualTo(36);
        assertThat(result.possibleExtraMonths()).isZero();
        assertThat(result.perExperience()).hasSize(1);
        assertThat(result.perExperience().get(0).note())
                .startsWith("Work in Norway (summary answer, no dates):")
                .contains("did not ask whether it was public or private");
    }

    @Test
    void workIsOnlyUncertainInKsWhenThePositionNeedsAnEducation() {
        SeniorityResult result = calculator.combine(NO_DATED, 0, onlyWork(27), NURSE, RULES);

        assertThat(result.countedMonths()).isZero();
        assertThat(result.possibleExtraMonths()).isEqualTo(27);
    }

    @Test
    void workCountsInFullInOsloWhateverThePosition() {
        SeniorityResult result = calculator.combine(NO_DATED, 0, onlyWork(27), NURSE, OSLO);

        assertThat(result.countedMonths()).isEqualTo(27);
        assertThat(result.possibleExtraMonths()).isZero();
    }

    @Test
    void ownFirmIsSplitOffAndIsOnlyUncertain() {
        WorkAnswer answer = new WorkAnswer(months(36), months(12), null);

        SeniorityResult result = run(screening(answer, null, null, null, null, null, PeriodOverlap.NO));

        assertThat(result.countedMonths()).isEqualTo(24);
        assertThat(result.possibleExtraMonths()).isEqualTo(12);
        assertThat(result.perExperience()).hasSize(2);
        assertThat(result.perExperience().get(1).note()).startsWith("Work in Norway, own firm").contains("tax records");
    }

    @Test
    void workAbroadIsTreatedLikeWorkInNorway() {
        SeniorityResult result = run(screening(null, work(24), null, null, null, null, null));

        assertThat(result.countedMonths()).isEqualTo(24);
        assertThat(result.perExperience().get(0).note()).startsWith("Work outside Norway");
    }

    @Test
    void auPairEarnsNothingAndSaysWhy() {
        SeniorityResult result = run(screening(null, null, new PeriodAnswer(months(18), null),
                null, null, null, null));

        assertThat(result.countedMonths()).isZero();
        assertThat(result.possibleExtraMonths()).isZero();
        assertThat(result.perExperience().get(0).note())
                .startsWith("Au pair (summary answer, no dates):")
                .contains("au pair is not credited by the rules we have modelled");
    }

    @Test
    void militaryServiceCountsInFullAndServiceAbroadGetsANote() {
        MilitaryAnswer norway = new MilitaryAnswer(months(12), "NO", null);
        MilitaryAnswer abroad = new MilitaryAnswer(months(12), "de", null);

        SeniorityResult inNorway = run(screening(null, null, null, norway, null, null, null));
        SeniorityResult inGermany = run(screening(null, null, null, abroad, null, null, null));

        assertThat(inNorway.countedMonths()).isEqualTo(12);
        assertThat(inNorway.perExperience().get(0).note()).doesNotContain("do not say");
        assertThat(inGermany.countedMonths()).isEqualTo(12);
        assertThat(inGermany.perExperience().get(0).note())
                .contains("do not say whether service abroad counts the same way");
    }

    @Test
    void childCareStopsAtTheAgreementLimit() {
        ScreeningDto fourYears = screening(null, null, null, null,
                new ChildCareAnswer(2, months(48), null), null, null);

        SeniorityResult ks = run(fourYears);
        SeniorityResult oslo = calculator.combine(NO_DATED, 0, fourYears, NURSE, OSLO);

        assertThat(ks.countedMonths()).isEqualTo(36);
        assertThat(ks.perExperience().get(0).note()).contains("limit was reached");
        assertThat(oslo.countedMonths()).isEqualTo(24);
    }

    @Test
    void theNumberOfChildrenDoesNotChangeTheResult() {
        ScreeningDto one = screening(null, null, null, null, new ChildCareAnswer(1, months(24), null), null, null);
        ScreeningDto five = screening(null, null, null, null, new ChildCareAnswer(5, months(24), null), null, null);

        assertThat(run(one).countedMonths()).isEqualTo(run(five).countedMonths()).isEqualTo(24);
    }

    @Test
    void otherCareSharesTheCareLimitWithChildCare() {
        ScreeningDto s = screening(null, null, null, null, new ChildCareAnswer(1, months(24), null),
                new OtherHomeAnswer(ExperienceKind.CARE_WORK, months(24), null), PeriodOverlap.NO);

        assertThat(run(s).countedMonths()).isEqualTo(36);
    }

    @Test
    void otherWorkInTheHomeFollowsTheHomeWorkRule() {
        ScreeningDto home = screening(null, null, null, null, null,
                new OtherHomeAnswer(ExperienceKind.HOME_WORK, months(30), null), null);

        // KS: counts only where no education is required.
        assertThat(run(home).countedMonths()).isEqualTo(30);
        assertThat(calculator.combine(NO_DATED, 0, home, NURSE, RULES).countedMonths()).isZero();

        // Oslo: only uncertain, and it shares the 2-year limit with care that counts.
        ScreeningDto oslo = screening(null, null, null, null, new ChildCareAnswer(1, months(12), null),
                new OtherHomeAnswer(ExperienceKind.HOME_WORK, months(36), null), PeriodOverlap.NO);
        SeniorityResult result = calculator.combine(NO_DATED, 0, oslo, NURSE, OSLO);
        assertThat(result.countedMonths()).isEqualTo(12);
        assertThat(result.possibleExtraMonths()).isEqualTo(12);
    }

    @Test
    void noOverlapAddsThePeriods() {
        ScreeningDto s = screening(work(36), null, null, null, new ChildCareAnswer(1, months(24), null), null,
                PeriodOverlap.NO);

        SeniorityResult result = run(s);

        assertThat(result.countedMonths()).isEqualTo(60);
        assertThat(result.possibleExtraMonths()).isZero();
    }

    @Test
    void overlapMakesTheResultARangeFromTheLargestPeriodToTheSum() {
        for (PeriodOverlap overlap : new PeriodOverlap[]{PeriodOverlap.YES, PeriodOverlap.UNSURE, null}) {
            ScreeningDto s = screening(work(36), null, null, null, new ChildCareAnswer(1, months(24), null), null,
                    overlap);

            SeniorityResult result = run(s);

            assertThat(result.countedMonths()).as("counted, overlap=" + overlap).isEqualTo(36);
            assertThat(result.possibleExtraMonths()).as("possible, overlap=" + overlap).isEqualTo(24);
            assertThat(result.perExperience().stream().mapToInt(PerExperience::countedMonths).sum()).isEqualTo(36);
            assertThat(result.perExperience().stream().mapToInt(PerExperience::possibleExtraMonths).sum())
                    .isEqualTo(24);
            assertThat(result.perExperience().get(0).note()).contains("a range");
        }
    }

    @Test
    void aSingleAnswerIsNeverARange() {
        assertThat(run(onlyWork(36)).possibleExtraMonths()).isZero();
        assertThat(run(screening(work(36), null, null, null, null, null, PeriodOverlap.YES)).possibleExtraMonths())
                .isZero();
    }

    @Test
    void auPairNeverTakesTimeFromAPeriodThatCounts() {
        ScreeningDto s = screening(work(24), null, new PeriodAnswer(months(24), null), null, null, null,
                PeriodOverlap.YES);

        SeniorityResult result = run(s);

        assertThat(result.countedMonths()).isEqualTo(24);
        assertThat(result.possibleExtraMonths()).isZero();
    }

    @Test
    void summaryAnswersAreCombinedWithTheDatedPeriods() {
        SeniorityResult dated = new SeniorityResult(24, 6, List.of(
                new PerExperience(0, 24, 0, "dated one"), new PerExperience(1, 0, 6, "dated two")));
        ScreeningDto no = screening(null, work(36), null, null, null, null, PeriodOverlap.NO);
        ScreeningDto yes = screening(null, work(36), null, null, null, null, PeriodOverlap.YES);

        SeniorityResult added = calculator.combine(dated, 2, no, ASSISTANT, RULES);
        SeniorityResult range = calculator.combine(dated, 2, yes, ASSISTANT, RULES);

        assertThat(added.countedMonths()).isEqualTo(60);
        assertThat(added.possibleExtraMonths()).isEqualTo(6);
        // Overlap possible: the low end is the larger block, the high end is everything added up.
        assertThat(range.countedMonths()).isEqualTo(36);
        assertThat(range.countedMonths() + range.possibleExtraMonths()).isEqualTo(24 + 6 + 36);
        // The dated entries come first and keep their numbers; summary entries follow.
        assertThat(added.perExperience()).hasSize(3);
        assertThat(added.perExperience().get(0).note()).isEqualTo("dated one");
        assertThat(added.perExperience().get(2).index()).isEqualTo(2);
    }

    @Test
    void emptyOrZeroAnswersChangeNothing() {
        SeniorityResult dated = new SeniorityResult(12, 0, List.of(new PerExperience(0, 12, 0, "x")));

        assertThat(calculator.combine(dated, 1, null, ASSISTANT, RULES)).isSameAs(dated);
        assertThat(calculator.combine(dated, 1, screening(work(0), null, new PeriodAnswer(null, null), null, null,
                null, null), ASSISTANT, RULES)).isSameAs(dated);
    }

    @Test
    void describeReportsWhatTheConfidenceLevelNeedsToKnow() {
        ScreeningSeniorityCalculator.Info none = calculator.describe(null, false);
        assertThat(none).isEqualTo(ScreeningSeniorityCalculator.Info.NONE);

        ScreeningDto exact = screening(new WorkAnswer(months(12), null, false), null, null, null, null, null, null);
        assertThat(calculator.describe(exact, false))
                .isEqualTo(new ScreeningSeniorityCalculator.Info(true, false, false));

        ScreeningDto approximateAndOverlapping = screening(new WorkAnswer(months(12), null, true), null, null, null,
                new ChildCareAnswer(1, months(6), null), null, PeriodOverlap.UNSURE);
        assertThat(calculator.describe(approximateAndOverlapping, false))
                .isEqualTo(new ScreeningSeniorityCalculator.Info(true, true, true));

        // One answer plus dated periods can also overlap.
        assertThat(calculator.describe(exact, true).overlapUncertain()).isTrue();
    }
}
