package com.tariffhelper.config;

import com.tariffhelper.llm.ClaudeLlmClient;
import com.tariffhelper.llm.ClaudeProperties;
import com.tariffhelper.llm.LlmClient;
import com.tariffhelper.llm.PromptBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmConfigTest {

    @Test
    void missingApiKeyFailsFast() {
        ClaudeProperties noKey = new ClaudeProperties("", "m", "https://api.anthropic.com", 1000, 5);
        assertThatThrownBy(() -> new LlmConfig().claudeLlmClient(noKey, new PromptBuilder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void withApiKeyCreatesClaudeClient() {
        ClaudeProperties props = new ClaudeProperties("k", "m", "https://api.anthropic.com", 1000, 5);
        LlmClient client = new LlmConfig().claudeLlmClient(props, new PromptBuilder());
        assertThat(client).isInstanceOf(ClaudeLlmClient.class);
    }
}
