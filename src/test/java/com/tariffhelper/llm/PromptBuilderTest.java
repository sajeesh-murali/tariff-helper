package com.tariffhelper.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

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
}
