package com.tariffhelper.config;

import com.tariffhelper.llm.AgreementSuggester;
import com.tariffhelper.llm.ChatAgreementSuggester;
import com.tariffhelper.llm.ChatApi;
import com.tariffhelper.llm.ChatLlmClient;
import com.tariffhelper.llm.ClaudeMessagesApi;
import com.tariffhelper.llm.ClaudeProperties;
import com.tariffhelper.llm.LlamaProperties;
import com.tariffhelper.llm.LlmClient;
import com.tariffhelper.llm.LlmProperties;
import com.tariffhelper.llm.OpenAiChatApi;
import com.tariffhelper.llm.OpenAiProperties;
import com.tariffhelper.llm.OpenRouterProperties;
import com.tariffhelper.llm.ChatPayslipFieldExtractor;
import com.tariffhelper.llm.PayslipFieldExtractor;
import com.tariffhelper.llm.PromptBuilder;
import com.tariffhelper.llm.StubAgreementSuggester;
import com.tariffhelper.llm.StubLlmClient;
import com.tariffhelper.llm.StubPayslipFieldExtractor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Picks the model behind both LLM calls (explanation and agreement suggestion) from
 * {@code app.llm.provider}: OPENROUTER (default, Llama model), LLAMA, OPENAI, ANTHROPIC, or STUB for no external calls.
 */
@Configuration
@EnableConfigurationProperties({LlmProperties.class, ClaudeProperties.class, OpenAiProperties.class,
        LlamaProperties.class, OpenRouterProperties.class})
public class LlmConfig {

    /** Not created for STUB, so the clients below fall back to the stubs. */
    @Bean
    @ConditionalOnExpression("!'${app.llm.provider:openrouter}'.equalsIgnoreCase('stub')")
    public ChatApi chatApi(LlmProperties llm, ClaudeProperties claude, OpenAiProperties openAi,
                           LlamaProperties llama, OpenRouterProperties openRouter) {
        return switch (llm.provider()) {
            case ANTHROPIC -> claudeApi(claude);
            case OPENAI -> openAiApi(openAi);
            case LLAMA -> llamaApi(llama);
            case OPENROUTER -> openRouterApi(openRouter);
            case STUB -> throw new IllegalStateException("No ChatApi for the stub provider");
        };
    }

    @Bean
    public LlmClient llmClient(ObjectProvider<ChatApi> api, PromptBuilder promptBuilder) {
        ChatApi chatApi = api.getIfAvailable();
        return chatApi == null ? new StubLlmClient() : new ChatLlmClient(chatApi, promptBuilder);
    }

    @Bean
    public AgreementSuggester agreementSuggester(ObjectProvider<ChatApi> api, PromptBuilder promptBuilder) {
        ChatApi chatApi = api.getIfAvailable();
        return chatApi == null ? new StubAgreementSuggester() : new ChatAgreementSuggester(chatApi, promptBuilder);
    }

    @Bean
    public PayslipFieldExtractor payslipFieldExtractor(ObjectProvider<ChatApi> api, PromptBuilder promptBuilder) {
        ChatApi chatApi = api.getIfAvailable();
        return chatApi == null ? new StubPayslipFieldExtractor() : new ChatPayslipFieldExtractor(chatApi, promptBuilder);
    }

    ChatApi claudeApi(ClaudeProperties props) {
        requireKey(props.apiKey(), "anthropic", "ANTHROPIC_API_KEY");
        return new ClaudeMessagesApi(restClient(props.baseUrl(), props.timeoutSeconds()), props);
    }

    ChatApi openAiApi(OpenAiProperties props) {
        requireKey(props.apiKey(), "openai", "OPENAI_API_KEY");
        return new OpenAiChatApi(restClient(props.baseUrl(), props.timeoutSeconds()), "OpenAI",
                props.model(), props.apiKey(), props.maxTokens(), "max_completion_tokens");
    }

    ChatApi llamaApi(LlamaProperties props) {
        return new OpenAiChatApi(restClient(props.baseUrl(), props.timeoutSeconds()), "Llama",
                props.model(), props.apiKey(), props.maxTokens(), "max_tokens");
    }

    ChatApi openRouterApi(OpenRouterProperties props) {
        requireKey(props.apiKey(), "openrouter", "OPENROUTER_API_KEY");
        // Some models (for example Claude Haiku 5.5) think before answering by default and can use the whole
        // token limit on that, which leaves an empty answer. The explanation needs no thinking.
        return new OpenAiChatApi(restClient(props.baseUrl(), props.timeoutSeconds()), "OpenRouter",
                props.model(), props.apiKey(), props.maxTokens(), "max_tokens",
                Map.of("reasoning", Map.of("enabled", false)));
    }

    private static void requireKey(String apiKey, String provider, String envVar) {
        if (apiKey == null || apiKey.isBlank()) {
            // Fail at startup rather than on the first user request.
            throw new IllegalStateException(
                    "app.llm.provider=" + provider + " but no API key is set. Set the " + envVar + " environment variable.");
        }
    }

    private static RestClient restClient(String baseUrl, int timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
