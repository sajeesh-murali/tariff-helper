package com.tariffhelper.config;

import com.tariffhelper.llm.AgreementSuggester;
import com.tariffhelper.llm.ChatAgreementSuggester;
import com.tariffhelper.llm.ChatApi;
import com.tariffhelper.llm.ChatLlmClient;
import com.tariffhelper.llm.ChatPayslipFieldExtractor;
import com.tariffhelper.llm.ClaudeMessagesApi;
import com.tariffhelper.llm.ClaudeProperties;
import com.tariffhelper.llm.LlamaProperties;
import com.tariffhelper.llm.LlmClient;
import com.tariffhelper.llm.LlmProperties;
import com.tariffhelper.llm.LlmProvider;
import com.tariffhelper.llm.OpenAiChatApi;
import com.tariffhelper.llm.OpenAiProperties;
import com.tariffhelper.llm.PayslipFieldExtractor;
import com.tariffhelper.llm.PromptBuilder;
import com.tariffhelper.llm.StubAgreementSuggester;
import com.tariffhelper.llm.StubLlmClient;
import com.tariffhelper.llm.StubPayslipFieldExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmConfigTest {

    private static final ClaudeProperties CLAUDE_NO_KEY =
            new ClaudeProperties("", "m", "https://api.anthropic.com", 1000, 5);
    private static final OpenAiProperties OPENAI_NO_KEY =
            new OpenAiProperties("", "m", "https://api.openai.com", 1000, 5);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LlmConfig.class, PromptBuilderConfig.class);

    @Configuration
    static class PromptBuilderConfig {
        @Bean
        PromptBuilder promptBuilder() {
            return new PromptBuilder();
        }
    }

    @Test
    void anthropicWithoutApiKeyFailsFast() {
        assertThatThrownBy(() -> new LlmConfig().claudeApi(CLAUDE_NO_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void openAiWithoutApiKeyFailsFast() {
        assertThatThrownBy(() -> new LlmConfig().openAiApi(OPENAI_NO_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void llamaProviderNeedsNoKey() {
        runner.withPropertyValues("app.llm.provider=llama").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ChatApi.class)).isInstanceOf(OpenAiChatApi.class);
            assertThat(context.getBean(ChatApi.class).model()).isEqualTo("llama3.1");
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(ChatLlmClient.class);
            assertThat(context.getBean(AgreementSuggester.class)).isInstanceOf(ChatAgreementSuggester.class);
        });
    }

    @Test
    void openRouterProviderUsesLlamaModelByDefault() {
        runner.withPropertyValues("app.llm.provider=openrouter", "app.llm.openrouter.api-key=k").run(context -> {
            assertThat(context.getBean(ChatApi.class)).isInstanceOf(OpenAiChatApi.class);
            assertThat(context.getBean(ChatApi.class).model()).isEqualTo("anthropic/claude-haiku-5.5");
        });
    }

    @Test
    void openRouterWithoutKeyStopsStartup() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void providerValueIsCaseInsensitive() {
        runner.withPropertyValues("app.llm.provider=Llama", "app.llm.llama.model=llama3.2")
                .run(context -> assertThat(context.getBean(ChatApi.class).model()).isEqualTo("llama3.2"));
    }

    @Test
    void openAiProviderUsesTheOpenAiSettings() {
        runner.withPropertyValues("app.llm.provider=OPENAI", "app.llm.openai.api-key=k", "app.llm.openai.model=gpt-x")
                .run(context -> {
                    assertThat(context.getBean(ChatApi.class)).isInstanceOf(OpenAiChatApi.class);
                    assertThat(context.getBean(ChatApi.class).model()).isEqualTo("gpt-x");
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(ChatLlmClient.class);
                });
    }

    @Test
    void anthropicProviderUsesTheClaudeSettings() {
        runner.withPropertyValues("app.llm.provider=ANTHROPIC", "app.llm.claude.api-key=k")
                .run(context -> {
                    assertThat(context.getBean(ChatApi.class)).isInstanceOf(ClaudeMessagesApi.class);
                    assertThat(context.getBean(AgreementSuggester.class)).isInstanceOf(ChatAgreementSuggester.class);
                });
    }

    @Test
    void openAiWithoutKeyStopsStartup() {
        runner.withPropertyValues("app.llm.provider=openai")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void stubProviderMakesNoChatApiAndUsesStubs() {
        runner.withPropertyValues("app.llm.provider=stub").run(context -> {
            assertThat(context).doesNotHaveBean(ChatApi.class);
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(StubLlmClient.class);
            assertThat(context.getBean(AgreementSuggester.class)).isInstanceOf(StubAgreementSuggester.class);
        });
    }

    @Test
    void payslipReadingFollowsTheProvider() {
        runner.withPropertyValues("app.llm.provider=stub").run(context ->
                assertThat(context.getBean(PayslipFieldExtractor.class)).isInstanceOf(StubPayslipFieldExtractor.class));
        runner.withPropertyValues("app.llm.provider=llama").run(context ->
                assertThat(context.getBean(PayslipFieldExtractor.class)).isInstanceOf(ChatPayslipFieldExtractor.class));
    }

    @Test
    void unknownProviderStopsStartup() {
        runner.withPropertyValues("app.llm.provider=gemini")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void openRouterIsTheDefaultInTheProperties() {
        runner.withPropertyValues("app.llm.openrouter.api-key=k").run(context -> assertThat(context.getBean(LlmProperties.class).provider())
                .isEqualTo(LlmProvider.OPENROUTER));
    }
}
