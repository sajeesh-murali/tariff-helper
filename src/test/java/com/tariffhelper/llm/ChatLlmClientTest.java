package com.tariffhelper.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tariffhelper.api.dto.EstimateRequest;
import com.tariffhelper.domain.EstimateFacts;
import com.tariffhelper.domain.ExperienceKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.tariffhelper.TestData.exp;
import static com.tariffhelper.TestData.nurseFacts;
import static com.tariffhelper.TestData.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** No real API calls: the HTTP layer is replaced by MockRestServiceServer. */
class ChatLlmClientTest {

    private static final String URL = "https://api.anthropic.com/v1/messages";

    private final ObjectMapper mapper = new ObjectMapper();

    private MockRestServiceServer server;
    private ChatLlmClient client;
    private EstimateRequest request;
    private EstimateFacts facts;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.com");
        server = MockRestServiceServer.bindTo(builder).build();
        ClaudeProperties props = new ClaudeProperties(
                "test-key", "test-model", "https://api.anthropic.com", 1000, 5);
        client = new ChatLlmClient(new ClaudeMessagesApi(builder.build(), props), new PromptBuilder());

        var experiences = List.of(exp(ExperienceKind.PUBLIC_SERVICE, null, "2018-03", "2020-06"));
        request = request(500_000, experiences);
        facts = nurseFacts(500_000, experiences);
    }

    @Test
    void sendsCorrectRequestAndReturnsTheExplanation() throws Exception {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.max_tokens").value(1000))
                .andExpect(jsonPath("$.system", containsString("only to explain the result")))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content", containsString("545,400")))
                .andRespond(withSuccess(reply("Your public service counts in full."), MediaType.APPLICATION_JSON));

        LlmAssessment result = client.assess(request, facts);

        assertThat(result.explanation()).isEqualTo("Your public service counts in full.");
        server.verify();
    }

    @Test
    void joinsSeveralTextBlocks() throws Exception {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(reply("First part. ", "Second part."), MediaType.APPLICATION_JSON));

        assertThat(client.assess(request, facts).explanation()).isEqualTo("First part. Second part.");
        server.verify();
    }

    @Test
    void retriesOnceWhenTheFirstAnswerIsEmpty() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(reply("   "), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(reply("A real answer."), MediaType.APPLICATION_JSON));

        assertThat(client.assess(request, facts).explanation()).isEqualTo("A real answer.");
        server.verify();
    }

    @Test
    void failsAfterTwoEmptyAnswers() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(reply(""), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(reply(""), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.assess(request, facts)).isInstanceOf(LlmException.class);
        server.verify();
    }

    @Test
    void cutsAVeryLongExplanationTo4000Characters() throws Exception {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(reply("x".repeat(5000)), MediaType.APPLICATION_JSON));

        assertThat(client.assess(request, facts).explanation()).hasSize(4000);
    }

    @Test
    void apiErrorBecomesLlmExceptionWithoutRetryAndWithoutLeakingTheBody() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("secret-error-body")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.assess(request, facts))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("401")
                .hasMessageNotContaining("secret-error-body");
        server.verify();
    }

    @Test
    void networkFailureBecomesLlmException() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> client.assess(request, facts))
                .isInstanceOf(LlmException.class)
                .hasMessage("Could not reach the AI service");
        server.verify();
    }

    /** Wraps text blocks the way the Anthropic Messages API does. */
    private String reply(String... textBlocks) throws JsonProcessingException {
        List<Map<String, String>> content = new ArrayList<>();
        for (String text : textBlocks) {
            content.add(Map.of("type", "text", "text", text));
        }
        return mapper.writeValueAsString(Map.of(
                "id", "msg_test",
                "type", "message",
                "role", "assistant",
                "content", content,
                "stop_reason", "end_turn",
                "usage", Map.of("input_tokens", 10, "output_tokens", 20)));
    }
}
