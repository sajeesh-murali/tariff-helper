package com.tariffhelper.service;

import com.tariffhelper.agreement.AgreementRepository;
import com.tariffhelper.domain.CreditedSeniority;
import com.tariffhelper.domain.SalaryStep;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreditedSeniorityCalculatorTest {

    private static final AgreementRepository REPOSITORY = new AgreementRepository();
    private static final YearMonth NOW = YearMonth.of(2026, 10);

    private final CreditedSeniorityCalculator calculator = new CreditedSeniorityCalculator();

    private static List<SalaryStep> ladder(String agreementId, String group) {
        return REPOSITORY.findById(agreementId).orElseThrow().salaryLadders().get(group);
    }

    @Test
    void osloNurseTrinn26MeansThreeCreditedYears() {
        CreditedSeniority result = calculator.calculate(ladder("oslo-kommune", "2601"), 26, null, NOW);

        assertThat(result.creditedYears()).isEqualByComparingTo("3");
        assertThat(result.basis()).isEqualTo("lonnstrinn");
        assertThat(result.stepNotOnLadder()).isFalse();
    }

    @Test
    void osloSpecialistTrinn37MeansTwelveCreditedYears() {
        assertThat(calculator.calculate(ladder("oslo-kommune", "3001"), 37, null, NOW).creditedYears())
                .isEqualByComparingTo("12");
    }

    @Test
    void theLowestFromYearsWithTheStepIsUsed() {
        List<SalaryStep> repeated = List.of(
                new SalaryStep(0, BigDecimal.ONE, 10),
                new SalaryStep(4, BigDecimal.TWO, 11),
                new SalaryStep(2, BigDecimal.TWO, 11));
        assertThat(calculator.calculate(repeated, 11, null, NOW).creditedYears()).isEqualByComparingTo("2");
    }

    @Test
    void trinnNotOnTheLadderIsFlaggedAndNotCompared() {
        CreditedSeniority result = calculator.calculate(
                ladder("oslo-kommune", "2601"), 40, YearMonth.of(2020, 1), NOW);

        assertThat(result.compared()).isFalse();
        assertThat(result.stepNotOnLadder()).isTrue();
    }

    @Test
    void ansiennitetsdatoGivesWholeMonthsDividedBy12() {
        // 2023-04 to 2026-10: 42 months = 3.5 years
        CreditedSeniority result = calculator.calculate(
                ladder("oslo-kommune", "2601"), null, YearMonth.of(2023, 4), NOW);

        assertThat(result.creditedYears()).isEqualByComparingTo("3.5");
        assertThat(result.basis()).isEqualTo("ansiennitetsdato");
    }

    @Test
    void ksHasNoStepNumbersSoOnlyAnsiennitetsdatoWorks() {
        CreditedSeniority withDate = calculator.calculate(ladder("ks-hta", "201"), 26, YearMonth.of(2016, 10), NOW);
        assertThat(withDate.creditedYears()).isEqualByComparingTo("10");
        assertThat(withDate.basis()).isEqualTo("ansiennitetsdato");
        assertThat(withDate.stepNumbersMissing()).isTrue();

        CreditedSeniority withoutDate = calculator.calculate(ladder("ks-hta", "201"), 26, null, NOW);
        assertThat(withoutDate.compared()).isFalse();
        assertThat(withoutDate.stepNumbersMissing()).isTrue();
    }

    @Test
    void noPayslipValuesMeansNoComparison() {
        assertThat(calculator.calculate(ladder("oslo-kommune", "2601"), null, null, NOW))
                .isEqualTo(CreditedSeniority.NONE);
    }
}
