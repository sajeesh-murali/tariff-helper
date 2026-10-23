package com.tariffhelper.config;

import com.tariffhelper.llm.AgreementSuggester;
import com.tariffhelper.llm.ClaudeAgreementSuggester;
import com.tariffhelper.llm.ClaudeLlmClient;
import com.tariffhelper.llm.ClaudeMessagesApi;
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
        assertThatThrownBy(() -> new LlmConfig().claudeMessagesApi(noKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void withApiKeyCreatesBothClaudeClients() {
        LlmConfig config = new LlmConfig();
        ClaudeProperties props = new ClaudeProperties("k", "m", "https://api.anthropic.com", 1000, 5);
        ClaudeMessagesApi api = config.claudeMessagesApi(props);

        LlmClient client = config.claudeLlmClient(api, props, new PromptBuilder());
        AgreementSuggester suggester = config.claudeAgreementSuggester(api, new PromptBuilder());

        assertThat(client).isInstanceOf(ClaudeLlmClient.class);
        assertThat(suggester).isInstanceOf(ClaudeAgreementSuggester.class);
    }
}
