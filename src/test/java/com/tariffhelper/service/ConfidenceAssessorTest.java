package com.tariffhelper.service;

import com.tariffhelper.api.dto.response.EstimateResponse.AgreementSuggestionInfo;
import com.tariffhelper.api.dto.response.EstimateResponse.Confidence;
import com.tariffhelper.domain.ConfidenceLevel;
import com.tariffhelper.domain.CreditedSeniority;
import com.tariffhelper.domain.PerExperience;
import com.tariffhelper.domain.SeniorityResult;
import com.tariffhelper.domain.SuggestionSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceAssessorTest {

    private final ConfidenceAssessor assessor = new ConfidenceAssessor();

    private static final SeniorityResult CERTAIN_12_YEARS = new SeniorityResult(144, 0, List.of());

    private static SeniorityResult uncertain(int periods) {
        List<PerExperience> per = IntStream.range(0, periods)
                .mapToObj(i -> new PerExperience(i, 0, 12, "uncertain"))
                .toList();
        return new SeniorityResult(0, 12 * periods, per);
    }

    private static AgreementSuggestionInfo suggestion(SuggestionSource source, ConfidenceLevel confidence) {
        return new AgreementSuggestionInfo("oslo-kommune", confidence, source, true);
    }

    private static CreditedSeniority credited(String years) {
        return new CreditedSeniority(new BigDecimal(years), CreditedSeniority.BASIS_LONNSTRINN, false, false);
    }

    @Test
    void agreementGivenAndNothingUncertainIsHigh() {
        Confidence c = assessor.assess(null, CERTAIN_12_YEARS, JobCategoryBasis.GIVEN, CreditedSeniority.NONE);

        assertThat(c.level()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(c.reasons()).contains("You chose the agreement", "No period depends on the employer's decision");
    }

    @Test
    void rulesMatchWithHighConfidenceIsHigh() {
        Confidence c = assessor.assess(suggestion(SuggestionSource.RULES, ConfidenceLevel.HIGH),
                CERTAIN_12_YEARS, JobCategoryBasis.GIVEN, CreditedSeniority.NONE);
        assertThat(c.level()).isEqualTo(ConfidenceLevel.HIGH);
    }

    @Test
    void uncertainPeriodsMakeItMediumAndAreCounted() {
        Confidence c = assessor.assess(null, uncertain(2), JobCategoryBasis.GIVEN, CreditedSeniority.NONE);

        assertThat(c.level()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(c.reasons()).contains("Two periods depend on the employer's decision");
        assertThat(assessor.assess(null, uncertain(1), JobCategoryBasis.GIVEN, CreditedSeniority.NONE).reasons())
                .contains("One period depends on the employer's decision");
    }

    @Test
    void mediumRulesMatchIsMedium() {
        assertThat(assessor.assess(suggestion(SuggestionSource.RULES, ConfidenceLevel.MEDIUM),
                CERTAIN_12_YEARS, JobCategoryBasis.GIVEN, CreditedSeniority.NONE).level())
                .isEqualTo(ConfidenceLevel.MEDIUM);
    }

    @Test
    void inferredJobGroupIsMedium() {
        Confidence byCode = assessor.assess(null, CERTAIN_12_YEARS, JobCategoryBasis.STILLINGSKODE,
                CreditedSeniority.NONE);
        Confidence byTitle = assessor.assess(null, CERTAIN_12_YEARS, JobCategoryBasis.JOB_TITLE,
                CreditedSeniority.NONE);

        assertThat(byCode.level()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(byCode.reasons()).anyMatch(r -> r.contains("stillingskode"));
        assertThat(byTitle.level()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(byTitle.reasons()).anyMatch(r -> r.contains("job title"));
    }

    @Test
    void aiSuggestionIsNeverHigh() {
        assertThat(assessor.assess(suggestion(SuggestionSource.AI, ConfidenceLevel.HIGH),
                CERTAIN_12_YEARS, JobCategoryBasis.GIVEN, CreditedSeniority.NONE).level())
                .isEqualTo(ConfidenceLevel.MEDIUM);
    }

    @Test
    void aiSuggestionBelowHighIsLow() {
        Confidence c = assessor.assess(suggestion(SuggestionSource.AI, ConfidenceLevel.MEDIUM),
                CERTAIN_12_YEARS, JobCategoryBasis.GIVEN, CreditedSeniority.NONE);

        assertThat(c.level()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(c.reasons()).anyMatch(r -> r.contains("suggested by AI"));
    }

    @Test
    void stepNotOnTheLadderIsLow() {
        Confidence c = assessor.assess(null, CERTAIN_12_YEARS, JobCategoryBasis.GIVEN,
                new CreditedSeniority(null, null, true, false));

        assertThat(c.level()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(c.reasons()).anyMatch(r -> r.contains("not on the pay ladder"));
    }

    @Test
    void payslipWithFewerYearsIsAConflict() {
        Confidence c = assessor.assess(null, CERTAIN_12_YEARS, JobCategoryBasis.GIVEN, credited("3"));

        assertThat(c.level()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(c.reasons()).contains(ConfidenceAssessor.FEWER_YEARS);
    }

    @Test
    void payslipWithMoreYearsIsAConflict() {
        Confidence c = assessor.assess(null, CERTAIN_12_YEARS, JobCategoryBasis.GIVEN, credited("13"));

        assertThat(c.level()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(c.reasons()).contains(ConfidenceAssessor.MORE_YEARS);
    }

    @Test
    void payslipWithinOneYearBelowIsNotAConflict() {
        Confidence c = assessor.assess(null, CERTAIN_12_YEARS, JobCategoryBasis.GIVEN, credited("11"));

        assertThat(c.level()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(c.reasons()).doesNotContain(ConfidenceAssessor.FEWER_YEARS, ConfidenceAssessor.MORE_YEARS);
    }

    @Test
    void stepNumbersMissingIsExplainedButDoesNotLowerTheLevel() {
        Confidence c = assessor.assess(null, CERTAIN_12_YEARS, JobCategoryBasis.GIVEN,
                new CreditedSeniority(null, null, false, true));

        assertThat(c.level()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(c.reasons()).anyMatch(r -> r.contains("cannot be checked"));
    }
}
