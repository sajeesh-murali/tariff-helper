package com.tariffhelper.api;

import com.jayway.jsonpath.JsonPath;
import com.tariffhelper.llm.ChatApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole path a person takes: upload {@code payslips/sample-payslip.pdf} to {@code POST /api/payslips/parse}, then
 * send the values it returned to {@code POST /api/estimates}. Everything runs for real (PDF reading, masking,
 * validation of what the model returned, agreement rules, seniority, pay lookup) except the network call: the
 * {@link ChatApi} is replaced, so no external model is called. The PDF holds invented data.
 */
@SpringBootTest(properties = "app.rate-limit.per-client-per-minute=1000")
@AutoConfigureMockMvc
class PayslipToEstimateIntegrationTest {

    /** What a model would copy from the excerpt of the sample PDF. Every value is written in that excerpt. */
    private static final String EXTRACTION_ANSWER = "{\"employerName\":\"Oslo kommune\",\"jobTitle\":\"Sykepleier\","
            + "\"positionPercent\":100,\"annualSalaryFullTime\":576000,\"stillingskode\":\"58\","
            + "\"lonnstrinn\":26,\"ansiennitetsdato\":\"2023-08\"}";

    private static final String EXPLANATION = "Your experience counts as 12 years in the Oslo kommune agreement.";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ChatApi chat;

    private static byte[] samplePdf() throws IOException {
        try (InputStream in = new ClassPathResource("payslips/sample-payslip.pdf").getInputStream()) {
            return in.readAllBytes();
        }
    }

    private String parseSamplePayslip() throws Exception {
        return mvc.perform(multipart("/api/payslips/parse")
                        .file(new MockMultipartFile("file", "payslip.pdf", "application/pdf", samplePdf())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void thePayslipIsReadAndItsValuesFeedTheEstimate() throws Exception {
        when(chat.maxTokens()).thenReturn(1000);
        when(chat.send(anyString(), anyString(), anyInt())).thenReturn(EXTRACTION_ANSWER, EXPLANATION);

        // Step 1: upload the payslip
        String parsed = parseSamplePayslip();

        assertThat(JsonPath.<String>read(parsed, "$.employerName")).isEqualTo("Oslo kommune");
        assertThat(JsonPath.<Integer>read(parsed, "$.positionPercent")).isEqualTo(100);
        assertThat(JsonPath.<Integer>read(parsed, "$.currentAnnualSalary")).isEqualTo(576000);
        assertThat(JsonPath.<String>read(parsed, "$.agreement.agreementId")).isEqualTo("oslo-kommune");
        assertThat(JsonPath.<String>read(parsed, "$.agreement.source")).isEqualTo("rules");
        assertThat(JsonPath.<String>read(parsed, "$.agreement.status")).isEqualTo("available");
        assertThat(JsonPath.<String>read(parsed, "$.agreement.jobCategory")).isEqualTo("nurse");

        // Only the labelled lines went to the model, with no personal data and no PDF.
        org.mockito.ArgumentCaptor<String> sent = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(chat, times(1)).send(anyString(), sent.capture(), anyInt());
        assertThat(sent.getValue())
                .contains("<user_data>")
                .contains("Arbeidsgiver: Oslo kommune")
                .contains("Årslønn i 100 % stilling før skatt 576 000,00")
                .contains("58 Sykepleier 26 01.08.2023")
                .doesNotContain("Kari").doesNotContain("Testesen")
                .doesNotContain("01019012345").doesNotContain("4202");

        // Step 2: the page sends the checked values, plus the work history, to the estimate
        String estimate = "{\"agreementId\":\"" + JsonPath.read(parsed, "$.agreement.agreementId") + "\","
                + "\"jobCategory\":\"" + JsonPath.read(parsed, "$.agreement.jobCategory") + "\","
                + "\"employerName\":\"" + JsonPath.read(parsed, "$.employerName") + "\","
                + "\"jobTitle\":\"" + JsonPath.read(parsed, "$.jobTitle") + "\","
                + "\"positionPercent\":" + JsonPath.read(parsed, "$.positionPercent") + ","
                + "\"currentAnnualSalary\":" + JsonPath.read(parsed, "$.currentAnnualSalary") + ","
                + "\"stillingskode\":\"" + JsonPath.read(parsed, "$.stillingskode") + "\","
                + "\"lonnstrinn\":" + JsonPath.read(parsed, "$.lonnstrinn") + ","
                + "\"ansiennitetsdato\":\"" + JsonPath.read(parsed, "$.ansiennitetsdato") + "\","
                + "\"experiences\":[{\"country\":\"NO\",\"kind\":\"public_service\","
                + "\"startDate\":\"2012-01\",\"endDate\":\"2023-12\"}]}";

        mvc.perform(post("/api/estimates").contentType(MediaType.APPLICATION_JSON).content(estimate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countedYears").value(12))
                // Oslo nurse (ramme 2601) at 12 completed years: 614600 (see oslo-kommune.yaml)
                .andExpect(jsonPath("$.estimatedMinimumSalary.min").value(614600))
                .andExpect(jsonPath("$.estimatedMinimumSalary.max").value(614600))
                .andExpect(jsonPath("$.differenceFromCurrent.min").value(38600))
                .andExpect(jsonPath("$.agreementSuggestion").doesNotExist())
                // Step 26 on the ladder is 3 years: the employer credits 9 years fewer than the rules count.
                .andExpect(jsonPath("$.creditedVsCounted.basis").value("lonnstrinn"))
                .andExpect(jsonPath("$.creditedVsCounted.creditedYears").value(3))
                .andExpect(jsonPath("$.creditedVsCounted.countedYears").value(12))
                .andExpect(jsonPath("$.confidence.level").value("medium"))
                .andExpect(jsonPath("$.explanation").value(EXPLANATION));

        // One call read the payslip, one wrote the explanation; the second never saw the payslip values.
        verify(chat, times(2)).send(anyString(), anyString(), anyInt());
        verify(chat, times(1)).send(anyString(), contains("Sykepleier"), anyInt());
    }

    @Test
    void aValueTheModelMadeUpIsDroppedAndDoesNotReachTheForm() throws Exception {
        // 600000 is not written anywhere in the payslip, so the salary (and the figure derived from it) is dropped.
        when(chat.send(anyString(), anyString(), anyInt())).thenReturn(
                "{\"employerName\":\"Oslo kommune\",\"positionPercent\":100,\"annualSalaryFullTime\":600000,"
                        + "\"lonnstrinn\":26}");

        String parsed = parseSamplePayslip();

        assertThat(JsonPath.<Object>read(parsed, "$.annualSalaryFullTime")).isNull();
        assertThat(JsonPath.<Object>read(parsed, "$.currentAnnualSalary")).isNull();
        assertThat(JsonPath.<Integer>read(parsed, "$.lonnstrinn")).isEqualTo(26);
        assertThat(JsonPath.<String>read(parsed, "$.agreement.agreementId")).isEqualTo("oslo-kommune");
    }

    @Test
    void ifTheModelIsDownThePersonStillGetsAnAnswerAndCanTypeTheValuesIn() throws Exception {
        when(chat.send(anyString(), anyString(), anyInt()))
                .thenThrow(new com.tariffhelper.llm.LlmException("down"));

        mvc.perform(multipart("/api/payslips/parse")
                        .file(new MockMultipartFile("file", "payslip.pdf", "application/pdf", samplePdf())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employerName").doesNotExist())
                .andExpect(jsonPath("$.agreement").doesNotExist())
                .andExpect(content().string(not(containsString("Kari"))));
    }

    @Test
    void theEstimateWorksFromTypedValuesWithoutAnyPayslip() throws Exception {
        when(chat.maxTokens()).thenReturn(1000);
        when(chat.send(anyString(), anyString(), anyInt())).thenReturn(EXPLANATION);

        mvc.perform(post("/api/estimates").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"employerName\":\"Bydel Grünerløkka\",\"jobTitle\":\"sykepleier\",\"positionPercent\":100,"
                                + "\"currentAnnualSalary\":576000,\"experiences\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreementSuggestion.agreementId").value("oslo-kommune"))
                .andExpect(jsonPath("$.agreementSuggestion.assumed").value(true))
                .andExpect(jsonPath("$.confidence.level").value("medium"));

        // Only the explanation was asked for; the rules decided the agreement, so no suggestion call.
        verify(chat, times(1)).send(anyString(), anyString(), anyInt());
    }
}
