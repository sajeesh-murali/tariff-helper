package com.tariffhelper.config;

import com.tariffhelper.llm.AgreementSuggester;
import com.tariffhelper.llm.ClaudeAgreementSuggester;
import com.tariffhelper.llm.ClaudeLlmClient;
import com.tariffhelper.llm.ClaudeMessagesApi;
import com.tariffhelper.llm.ClaudeProperties;
import com.tariffhelper.llm.LlmClient;
import com.tariffhelper.llm.PromptBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Wires the real Claude clients (explanation and agreement suggestion) when {@code app.llm.provider=claude}.
 * With any other value (default: stub) the StubLlmClient and StubAgreementSuggester are used instead.
 */
@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "claude")
    public ClaudeMessagesApi claudeMessagesApi(ClaudeProperties props) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            // Fail at startup rather than on the first user request.
            throw new IllegalStateException(
                    "app.llm.provider=claude but no API key is set. Set the ANTHROPIC_API_KEY environment variable.");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(props.timeoutSeconds()));

        RestClient restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new ClaudeMessagesApi(restClient, props);
    }

    @Bean
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "claude")
    public LlmClient claudeLlmClient(ClaudeMessagesApi api, ClaudeProperties props, PromptBuilder promptBuilder) {
        return new ClaudeLlmClient(api, props, promptBuilder);
    }

    @Bean
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "claude")
    public AgreementSuggester claudeAgreementSuggester(ClaudeMessagesApi api, PromptBuilder promptBuilder) {
        return new ClaudeAgreementSuggester(api, promptBuilder);
    }
}
