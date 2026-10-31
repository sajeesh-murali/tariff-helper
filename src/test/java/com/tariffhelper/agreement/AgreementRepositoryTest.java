package com.tariffhelper.agreement;

import com.tariffhelper.domain.Agreement;
import com.tariffhelper.domain.HomeWorkRule;
import com.tariffhelper.domain.JobCategory;
import com.tariffhelper.domain.PrivateServiceRule;
import com.tariffhelper.domain.Rules;
import com.tariffhelper.domain.SalaryStep;
import com.tariffhelper.service.SalaryEstimator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the real rules files. The expected figures are the KS minimum-pay table valid from
 * 1 May 2026 ("Garantilønn/laveste årslønn og tillegg for ansiennitet kapittel 4"), both the
 * resulting salaries and the published step increases. A typo in ks-hta.yaml fails here.
 */
class AgreementRepositoryTest {

    private final AgreementRepository repository = new AgreementRepository();

    private Agreement ks() {
        return repository.findById("ks-hta").orElseThrow();
    }

    private Agreement oslo() {
        return repository.findById("oslo-kommune").orElseThrow();
    }

    @Test
    void loadsAllAgreements() {
        assertThat(repository.findAll()).extracting(Agreement::id)
                .containsExactly("fus", "ks-hta", "oslo-kommune", "pbl", "spekter", "staten",
                        "virke-barnehager", "virke-helse");
    }

    @Test
    void ksAndOsloHaveRulesStatenAndSpekterDoNotYet() {
        assertThat(ks().hasRules()).isTrue();
        assertThat(oslo().hasRules()).isTrue();
        assertThat(repository.findById("staten").orElseThrow().hasRules()).isFalse();
        assertThat(repository.findById("spekter").orElseThrow().hasRules()).isFalse();
    }

    @Test
    void ksRuleLimitsAreThreeYearsOfCareAndSixYearsOfHomeWork() {
        assertThat(ks().rules().careWorkMaxYears()).isEqualTo(3);
        assertThat(ks().rules().homeWorkMaxYears()).isEqualTo(6);
        assertThat(ks().rules().privateService()).isEqualTo(PrivateServiceRule.BY_EDUCATION_REQUIREMENT);
        assertThat(ks().rules().homeWork()).isEqualTo(HomeWorkRule.IF_NO_EDUCATION_REQUIREMENT);
    }

    @Test
    void osloRulesCountAllPrivateServiceAndLimitCareToTwoYears() {
        assertThat(oslo().rules().careWorkMaxYears()).isEqualTo(2);
        assertThat(oslo().rules().privateService()).isEqualTo(PrivateServiceRule.ALWAYS_FULL);
        assertThat(oslo().rules().homeWork()).isEqualTo(HomeWorkRule.POSSIBLE_ONLY);
        assertThat(oslo().effectiveFrom()).isEqualTo("2026-05-01");
        assertThat(oslo().caveats()).isNotEmpty();
    }

    /** Year-by-year pay (lowest alternativ) from the NSF Oslo kommune tables valid from 1 May 2026. */
    @Test
    void osloNursePayMatchesTheMay2026YearByYearTable() {
        int[] nurse2601 = {559_350, 566_600, 571_000, 576_000, 576_000, 576_000, 581_200, 586_800, 592_700,
                599_100, 606_200, 606_200, 614_600, 623_400, 633_200, 643_000, 654_100};
        int[] specialist3001 = {586_800, 586_800, 614_600, 614_600, 623_400, 623_400, 623_400, 623_400, 623_400,
                633_200, 643_000, 643_000, 666_400, 666_400, 666_400, 689_300, 726_900};
        int[] clinical3009 = {654_100, 654_100, 702_200, 702_200, 714_100, 714_100, 714_100, 714_100, 714_100,
                726_900, 740_600, 740_600, 767_500, 767_500, 767_500, 798_200, 844_000};
        assertYearByYear("2601", nurse2601);
        assertYearByYear("3001", specialist3001);
        assertYearByYear("3009", clinical3009);
    }

    /** Pay step number (lønnstrinn) per ladder entry, from the same NSF tables. Format: fromYears, step. */
    @Test
    void osloStepNumbersMatchTheMay2026Table() {
        assertSteps("2601", 0, 23, 1, 24, 2, 25, 3, 26, 6, 27, 7, 28, 8, 29, 9, 30, 10, 31, 12, 32, 13, 33,
                14, 34, 15, 35, 16, 36);
        assertSteps("3001", 0, 28, 2, 32, 4, 33, 9, 34, 10, 35, 12, 37, 15, 39, 16, 42);
        assertSteps("3009", 0, 36, 2, 40, 4, 41, 9, 42, 10, 43, 12, 45, 15, 47, 16, 50);
    }

    @Test
    void ksHasNoStepNumbersYet() {
        assertThat(ks().salaryLadders().values()).allSatisfy(ladder ->
                assertThat(ladder).extracting(SalaryStep::step).containsOnlyNulls());
    }

    @Test
    void osloJobCategoriesAreMappedToTheRightPayFrame() {
        assertThat(oslo().findJobCategory("nurse").orElseThrow().salaryGroup()).isEqualTo("2601");
        assertThat(oslo().findJobCategory("specialist_nurse").orElseThrow().salaryGroup()).isEqualTo("3001");
        assertThat(oslo().findJobCategory("clinical_nurse").orElseThrow().salaryGroup()).isEqualTo("3009");
    }

    @Test
    void ksPayLaddersMatchTheMay2026Table() {
        // steps: 0, 6, 8, 10, 16 years
        assertLadder("101", 418_600, 421_600, 429_700, 476_000, 515_800);
        assertLadder("102", 475_300, 479_500, 490_900, 534_700, 551_100);
        assertLadder("201", 545_400, 558_400, 568_600, 620_800, 639_900);
        assertLadder("202", 586_400, 601_800, 622_000, 647_900, 673_200);
    }

    @Test
    void ksPayLaddersMatchThePublishedStepIncreases() {
        assertIncreases("101", 3_000, 8_100, 46_300, 39_800);
        assertIncreases("102", 4_200, 11_400, 43_800, 16_400);
        assertIncreases("201", 13_000, 10_200, 52_200, 19_100);
        assertIncreases("202", 15_400, 20_200, 25_900, 25_300);
    }

    @Test
    void ksJobCategoriesAreMappedToTheRightGroupAndEducationFlag() {
        JobCategory nurse = ks().findJobCategory("nurse").orElseThrow();
        assertThat(nurse.requiresEducation()).isTrue();
        assertThat(nurse.salaryGroup()).isEqualTo("201");

        JobCategory teacher = ks().findJobCategory("kindergarten_teacher").orElseThrow();
        assertThat(teacher.requiresEducation()).isTrue();
        assertThat(teacher.salaryGroup()).isEqualTo("201");

        JobCategory assistant = ks().findJobCategory("kindergarten_assistant").orElseThrow();
        assertThat(assistant.requiresEducation()).isFalse();
        assertThat(assistant.salaryGroup()).isEqualTo("101");
    }

    @Test
    void privateAgreementsCountAllPrivateServiceAndAreMarkedAvailable() {
        for (String id : List.of("pbl", "virke-barnehager", "virke-helse", "fus")) {
            Agreement a = repository.findById(id).orElseThrow();
            assertThat(a.hasRules()).as(id).isTrue();
            assertThat(a.rules().careWorkMaxYears()).as(id).isEqualTo(3);
            assertThat(a.rules().homeWorkMaxYears()).as(id).isEqualTo(6);
            assertThat(a.rules().privateService()).as(id).isEqualTo(PrivateServiceRule.ALWAYS_FULL);
            assertThat(a.rules().homeWork()).as(id).isEqualTo(HomeWorkRule.IF_NO_EDUCATION_REQUIREMENT);
            assertThat(a.caveats()).as(id).isNotEmpty();
            assertThat(a.rulesSummary()).as(id).contains("3 years").contains("6 years");
        }
    }

    @Test
    void pblPayMatchesTheMay2026MediationTable() {
        // steps: 0, 4, 8, 10, 16 years (preliminary table, member vote pending when sourced)
        assertLadder("pbl", "assistant", new int[] {0, 4, 8, 10, 16}, 420_500, 427_800, 440_900, 483_000, 519_300);
        assertLadder("pbl", "skilled-worker", new int[] {0, 4, 8, 10, 16}, 478_400, 489_000, 501_600, 543_700, 559_900);
        assertLadder("pbl", "kindergarten-teacher", new int[] {0, 4, 8, 10, 16}, 558_400, 562_800, 577_400, 634_600, 651_800);
        assertLadder("pbl", "pedagogical-leader", new int[] {0, 4, 8, 10, 16}, 593_400, 597_800, 612_400, 670_100, 687_300);
        assertLadder("pbl", "special-educator", new int[] {0, 4, 8, 10, 16}, 594_200, 602_600, 613_300, 671_300, 689_700);
        assertThat(repository.findById("pbl").orElseThrow().caveats()).anyMatch(c -> c.contains("avstemning"));
    }

    @Test
    void virkeAgreementsUseTheSharedMay2026Table() {
        int[] years = {0, 6, 8, 10, 16};
        for (String id : List.of("virke-barnehager", "virke-helse")) {
            assertLadder(id, "no-education", years, 418_600, 421_600, 429_700, 476_000, 515_800);
            assertLadder(id, "skilled-worker", years, 475_300, 479_500, 490_900, 534_700, 551_100);
            assertLadder(id, "3-year-degree", years, 545_400, 558_400, 568_600, 620_800, 639_900);
        }
    }

    @Test
    void virkeJobCategoriesAreMappedToTheRightGroup() {
        Agreement helse = repository.findById("virke-helse").orElseThrow();
        assertThat(helse.findJobCategory("nurse").orElseThrow().salaryGroup()).isEqualTo("3-year-degree");
        assertThat(helse.findJobCategory("care_assistant").orElseThrow().requiresEducation()).isFalse();
        Agreement bhg = repository.findById("virke-barnehager").orElseThrow();
        assertThat(bhg.findJobCategory("kindergarten_teacher").orElseThrow().salaryGroup()).isEqualTo("3-year-degree");
        assertThat(bhg.findJobCategory("kindergarten_assistant").orElseThrow().requiresEducation()).isFalse();
    }

    @Test
    void fusUsesTheMay2025TableAndSaysSo() {
        Agreement fus = repository.findById("fus").orElseThrow();
        assertThat(fus.effectiveFrom()).isEqualTo("2025-05-01");
        int[] years = {0, 4, 8, 10, 16};
        assertLadder("fus", "assistant", years, 403_800, 410_600, 422_700, 463_900, 499_300);
        assertLadder("fus", "child-youth-worker", years, 459_900, 470_000, 481_600, 522_700, 537_900);
        assertLadder("fus", "kindergarten-teacher", years, 536_400, 540_600, 554_600, 609_600, 626_100);
        assertLadder("fus", "pedagogical-leader", years, 569_900, 574_100, 588_100, 643_600, 660_100);
        assertLadder("fus", "special-educator", years, 570_200, 578_300, 588_400, 644_100, 661_800);
        assertThat(fus.caveats()).anyMatch(c -> c.contains("forlenget gyldighet"));
    }

    private void assertLadder(String agreementId, String group, int[] fromYears, int... expected) {
        List<SalaryStep> steps = repository.findById(agreementId).orElseThrow().salaryLadders().get(group);
        assertThat(steps).as("%s ladder %s", agreementId, group).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) {
            final int from = fromYears[i];
            SalaryStep step = steps.stream().filter(x -> x.fromYears() == from).findFirst().orElseThrow();
            assertThat(step.annualSalary()).as("%s %s from %d years", agreementId, group, from)
                    .isEqualByComparingTo(String.valueOf(expected[i]));
        }
    }

    @Test
    void ksFileHasRulesSummaryAndCaveats() {
        assertThat(ks().rulesSummary()).contains("lønnsansiennitet").contains("3 years").contains("6 years");
        assertThat(ks().caveats()).isNotEmpty();
        assertThat(ks().effectiveFrom()).isEqualTo("2026-05-01");
    }

    @Test
    void validationRejectsAJobCategoryWithAnUnknownSalaryGroup() {
        Agreement broken = agreementWith(
                List.of(new JobCategory("nurse", "Nurse", true, "999", null)),
                Map.of("201", List.of(new SalaryStep(0, BigDecimal.TEN))));
        assertThatThrownBy(() -> AgreementRepository.validate(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("salary group");
    }

    @Test
    void validationRejectsAJobCategoryWithoutTheEducationFlag() {
        Agreement broken = agreementWith(
                List.of(new JobCategory("nurse", "Nurse", null, "201", null)),
                Map.of("201", List.of(new SalaryStep(0, BigDecimal.TEN))));
        assertThatThrownBy(() -> AgreementRepository.validate(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("educationRequired");
    }

    @Test
    void validationRejectsALadderWithoutAZeroYearStep() {
        Agreement broken = agreementWith(
                List.of(new JobCategory("nurse", "Nurse", true, "201", null)),
                Map.of("201", List.of(new SalaryStep(6, BigDecimal.TEN))));
        assertThatThrownBy(() -> AgreementRepository.validate(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("0 years");
    }

    private static Agreement agreementWith(List<JobCategory> categories, Map<String, List<SalaryStep>> ladders) {
        return new Agreement("test", "Test", "test", "available", "2026-05-01",
                categories, Rules.of(3, 6), ladders, "summary", List.of(), null);
    }

    private void assertYearByYear(String group, int[] expectedByYear) {
        SalaryEstimator estimator = new SalaryEstimator();
        List<SalaryStep> ladder = oslo().salaryLadders().get(group);
        for (int year = 0; year < expectedByYear.length; year++) {
            assertThat(estimator.minimumSalary(ladder, year * 12))
                    .as("ramme %s after %d years", group, year)
                    .isEqualByComparingTo(String.valueOf(expectedByYear[year]));
        }
    }

    private void assertSteps(String group, int... fromYearsAndStep) {
        List<SalaryStep> ladder = oslo().salaryLadders().get(group);
        assertThat(ladder).as("ramme %s", group).hasSize(fromYearsAndStep.length / 2);
        for (int i = 0; i < fromYearsAndStep.length; i += 2) {
            final int from = fromYearsAndStep[i];
            SalaryStep step = ladder.stream().filter(s -> s.fromYears() == from).findFirst().orElseThrow();
            assertThat(step.step()).as("ramme %s from %d years", group, from).isEqualTo(fromYearsAndStep[i + 1]);
        }
    }

    private void assertLadder(String group, int... expected) {
        int[] fromYears = {0, 6, 8, 10, 16};
        List<SalaryStep> steps = ks().salaryLadders().get(group);
        assertThat(steps).as("ladder %s", group).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) {
            final int from = fromYears[i];
            SalaryStep step = steps.stream().filter(s -> s.fromYears() == from).findFirst().orElseThrow();
            assertThat(step.annualSalary()).as("group %s from %d years", group, from)
                    .isEqualByComparingTo(String.valueOf(expected[i]));
        }
    }

    private void assertIncreases(String group, int... expectedIncreases) {
        List<SalaryStep> steps = ks().salaryLadders().get(group).stream()
                .sorted((a, b) -> Integer.compare(a.fromYears(), b.fromYears()))
                .toList();
        for (int i = 0; i < expectedIncreases.length; i++) {
            BigDecimal increase = steps.get(i + 1).annualSalary().subtract(steps.get(i).annualSalary());
            assertThat(increase).as("group %s step %d", group, i + 1)
                    .isEqualByComparingTo(String.valueOf(expectedIncreases[i]));
        }
    }
}
