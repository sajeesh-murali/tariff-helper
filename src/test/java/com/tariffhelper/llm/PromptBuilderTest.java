package com.tariffhelper.llm;

import com.tariffhelper.api.EstimateRequest;
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
    void emptyHistoryIsStated() {
        List<com.tariffhelper.api.ExperienceDto> none = List.of();
        String user = builder.userPrompt(request(500_000, none), nurseFacts(500_000, none));
        assertThat(user).contains("no previous work history entered");
    }
}
