package com.tariffhelper.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** No real API calls: the HTTP layer is replaced by MockRestServiceServer. */
class OpenAiChatApiTest {

    private static final String URL = "http://localhost:11434/v1/chat/completions";
    private static final String ANSWER = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"},"
            + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2}}";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://localhost:11434");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private OpenAiChatApi api(String apiKey, String maxTokensField) {
        return new OpenAiChatApi(builder.build(), "Test", "test-model", apiKey, 1000, maxTokensField);
    }

    @Test
    void sendsSystemAndUserMessagesAndReturnsTheAnswer() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.max_tokens").value(321))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("be brief"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("hi"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

        assertThat(api("", "max_tokens").send("be brief", "hi", 321)).isEqualTo("Hello");
        server.verify();
    }

    @Test
    void sendsBearerKeyAndTheConfiguredTokenField() {
        server.expect(requestTo(URL))
                .andExpect(header("Authorization", "Bearer secret"))
                .andExpect(jsonPath("$.max_completion_tokens").value(50))
                .andExpect(jsonPath("$.max_tokens").doesNotExist())
                .andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

        api("secret", "max_completion_tokens").send("s", "u", 50);
        server.verify();
    }

    @Test
    void missingContentGivesAnEmptyAnswer() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        assertThat(api("", "max_tokens").send("s", "u", 50)).isEmpty();
    }

    @Test
    void httpErrorBecomesLlmExceptionWithoutEchoingTheBody() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret-detail"));
        assertThatThrownBy(() -> api("k", "max_tokens").send("s", "u", 50))
                .isInstanceOf(LlmException.class)
                .hasMessage("The AI service returned an error (HTTP 401)");
    }

    @Test
    void connectionFailureBecomesLlmException() {
        server.expect(requestTo(URL)).andRespond(withException(new java.io.IOException("refused")));
        assertThatThrownBy(() -> api("", "max_tokens").send("s", "u", 50))
                .isInstanceOf(LlmException.class)
                .hasMessage("Could not reach the AI service");
    }
}
