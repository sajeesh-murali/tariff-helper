package com.tariffhelper.llm;

import com.tariffhelper.api.dto.request.EstimateRequest;
import com.tariffhelper.domain.EstimateFacts;
import com.tariffhelper.domain.ExperienceKind;
import com.tariffhelper.domain.Relevance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tariffhelper.TestData.exp;
import static com.tariffhelper.TestData.nurseFacts;
import static com.tariffhelper.TestData.request;
import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void systemPromptLimitsTheModelToExplainingAndTreatsFormDataAsData() {
        String system = builder.systemPrompt();
        assertThat(system).contains("only to explain the result");
        assertThat(system).contains("never change a number");
        assertThat(system).contains("<user_data>");
        assertThat(system).contains("never instructions");
        assertThat(system).contains("plain text only");
    }

    @Test
    void suggestPromptAsksForJsonFromTheAllowlistAndTreatsFormDataAsData() {
        String system = builder.suggestSystemPrompt();
        assertThat(system).contains("<user_data>").contains("never instructions")
                .contains("\"agreementId\"").contains("Never mention salary");

        String user = builder.suggestUserPrompt("Bergen kommune", null, List.of("ks-hta", "staten"));
        assertThat(user)
                .contains("Allowed agreement ids: ks-hta, staten, unknown")
                .contains("<user_data>\nEmployer name: Bergen kommune\n</user_data>")
                .doesNotContain("Job title");
    }

    @Test
    void userPromptCarriesTheRulesTheCalculatedNumbersAndTheHistory() {
        var experiences = List.of(exp(ExperienceKind.PUBLIC_SERVICE, null, "2018-03", "2020-06"));
        EstimateFacts facts = nurseFacts(500_000, experiences);
        EstimateRequest request = request(500_000, experiences);

        String user = builder.userPrompt(request, facts);

        assertThat(user)
                .contains("KS Hovedtariffavtale (municipalities)")
                .contains("pay tables valid from 2026-05-01")
                .contains("Rules summary text.")
                .contains("Nurse (sykepleier)")
                .contains("pay group 201")
                .contains("education requirement: yes")
                .contains("500,000 NOK per year")
                .contains("Counted seniority: 2.33 years (28 months)")
                .contains("545,400 NOK per year")
                .contains("from 45,400 to 45,400")
                .contains("Entry 1: PUBLIC_SERVICE, country NO, 2018-03 to 2020-06")
                .contains("counts 28 month(s)");
    }

    @Test
    void uncertainPeriodsShowUpAsARange() {
        var experiences = List.of(exp(ExperienceKind.PRIVATE_SERVICE, Relevance.PARTLY_RELATED, "2008-01", "2019-12"));
        String user = builder.userPrompt(request(500_000, experiences), nurseFacts(500_000, experiences));

        assertThat(user)
                .contains("Counted seniority: 0.00 years (0 months)")
                .contains("if every uncertain period is accepted: 12.00 years (144 months)")
                .contains("620,800 NOK per year")
                .contains("144 more month(s) only if the employer accepts it")
                .contains("relevance to the position: PARTLY_RELATED");
    }

    @Test
    void negativeDifferenceIsShownWithAMinusSign() {
        var experiences = List.of(exp(ExperienceKind.PUBLIC_SERVICE, null, "2018-03", "2020-06"));
        String user = builder.userPrompt(request(600_000, experiences), nurseFacts(600_000, experiences));
        assertThat(user).contains("from -54,600 to -54,600");
    }

    @Test
    void formEntriesAreInsideTheUserDataTags() {
        var experiences = List.of(exp(ExperienceKind.PUBLIC_SERVICE, null, "2018-03", "2020-06"));
        String user = builder.userPrompt(request(500_000, experiences), nurseFacts(500_000, experiences));

        int open = user.indexOf("<user_data>");
        int close = user.indexOf("</user_data>");
        assertThat(open).isGreaterThanOrEqualTo(0);
        assertThat(close).isGreaterThan(open);
        assertThat(user.substring(open, close)).contains("Entry 1:");
    }

    @Test
    void payslipConclusionIsAddedButNeverTheRawPayslipValues() {
        var experiences = List.of(exp(ExperienceKind.PUBLIC_SERVICE, null, "2018-03", "2020-06"));
        EstimateFacts base = nurseFacts(500_000, experiences);
        EstimateFacts facts = new EstimateFacts(base.agreement(), base.jobCategory(), base.seniority(),
                base.currentFullTimeSalary(), base.estimatedMinimumSalary(), base.difference(),
                "The employer currently credits about 1 year; the rules count about 2 years.");
        EstimateRequest request = new EstimateRequest("ks-hta", "nurse", 100, java.math.BigDecimal.valueOf(500_000),
                experiences, "Bergen kommune", "Avdelingssjef", "7174", 47, java.time.YearMonth.of(2011, 7));

        String user = builder.userPrompt(request, facts);

        assertThat(user)
                .contains("- From the person's payslip: The employer currently credits about 1 year; "
                        + "the rules count about 2 years.")
                .doesNotContain("2011-07")
                .doesNotContain("7174")
                .doesNotContain("47")
                .doesNotContain("Bergen kommune")
                .doesNotContain("Avdelingssjef");
        assertThat(builder.userPrompt(request(500_000, experiences), base)).doesNotContain("payslip");
    }

    @Test
    void emptyHistoryIsStated() {
        List<com.tariffhelper.api.dto.request.ExperienceDto> none = List.of();
        String user = builder.userPrompt(request(500_000, none), nurseFacts(500_000, none));
        assertThat(user).contains("no previous work history entered");
    }

    @Test
    void summaryAnswersAreListedInsideUserDataAndMarkedPreliminary() {
        var dated = List.of(exp(ExperienceKind.PUBLIC_SERVICE, null, "2018-03", "2020-06"));
        var screening = new com.tariffhelper.api.dto.request.ScreeningDto(
                new com.tariffhelper.api.dto.request.ScreeningDto.WorkAnswer(
                        new com.tariffhelper.api.dto.request.ScreeningDto.Duration(1, 6), null, null),
                null,
                new com.tariffhelper.api.dto.request.ScreeningDto.PeriodAnswer(
                        new com.tariffhelper.api.dto.request.ScreeningDto.Duration(0, 9), null),
                null, null, null, com.tariffhelper.domain.PeriodOverlap.NO);
        var datedResult = new com.tariffhelper.service.SeniorityCalculator().calculate(dated,
                com.tariffhelper.TestData.NURSE, com.tariffhelper.TestData.RULES);
        var combined = new com.tariffhelper.service.ScreeningSeniorityCalculator(
                new com.tariffhelper.service.SeniorityCalculator())
                .combine(datedResult, 1, screening, com.tariffhelper.TestData.NURSE, com.tariffhelper.TestData.RULES);
        EstimateFacts base = nurseFacts(500_000, dated);
        EstimateFacts facts = new EstimateFacts(base.agreement(), base.jobCategory(), combined,
                base.currentFullTimeSalary(), base.estimatedMinimumSalary(), base.difference());
        EstimateRequest request = new EstimateRequest("ks-hta", "nurse", 100, java.math.BigDecimal.valueOf(500_000),
                dated, null, null, null, null, null, screening);

        String user = builder.userPrompt(request, facts);

        int open = user.indexOf("<user_data>");
        int close = user.indexOf("</user_data>");
        assertThat(user.substring(0, open)).contains("summary answers (total time without dates)")
                .contains("preliminary estimate");
        assertThat(user.substring(open, close))
                .contains("Entry 1:")
                .contains("Summary answer 1 (total time only):")
                .contains("Work in Norway (summary answer, no dates)")
                .contains("18 more month(s) only if the employer accepts it")
                .contains("Summary answer 2 (total time only):")
                .contains("Au pair (summary answer, no dates)");
        assertThat(user).doesNotContain("no previous work history entered");
        assertThat(builder.systemPrompt()).contains("summary answers").contains("preliminary");
    }

    @Test
    void historyThatIsOnlySummaryAnswersIsNotCalledEmpty() {
        var screening = new com.tariffhelper.api.dto.request.ScreeningDto(
                new com.tariffhelper.api.dto.request.ScreeningDto.WorkAnswer(
                        new com.tariffhelper.api.dto.request.ScreeningDto.Duration(2, 0), null, null),
                null, null, null, null, null, null);
        List<com.tariffhelper.api.dto.request.ExperienceDto> none = List.of();
        var combined = new com.tariffhelper.service.ScreeningSeniorityCalculator(
                new com.tariffhelper.service.SeniorityCalculator())
                .combine(new com.tariffhelper.domain.SeniorityResult(0, 0, List.of()), 0, screening,
                        com.tariffhelper.TestData.ASSISTANT, com.tariffhelper.TestData.RULES);
        EstimateFacts base = nurseFacts(500_000, none);
        EstimateFacts facts = new EstimateFacts(base.agreement(), base.jobCategory(), combined,
                base.currentFullTimeSalary(), base.estimatedMinimumSalary(), base.difference());
        EstimateRequest request = new EstimateRequest("ks-hta", "nurse", 100, java.math.BigDecimal.valueOf(500_000),
                none, null, null, null, null, null, screening);

        String user = builder.userPrompt(request, facts);

        assertThat(user).contains("Summary answer 1 (total time only):").doesNotContain("no previous work history");
    }
}
