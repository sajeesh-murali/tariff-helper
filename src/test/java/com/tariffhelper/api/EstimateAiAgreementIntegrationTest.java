package com.tariffhelper.api;

import com.tariffhelper.domain.ConfidenceLevel;
import com.tariffhelper.llm.AgreementSuggester;
import com.tariffhelper.llm.AgreementSuggestion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The suggester is mocked, so no external calls are made and the AI path of the estimate can be tested. */
@SpringBootTest(properties = "app.rate-limit.per-client-per-minute=1000")
@AutoConfigureMockMvc
class EstimateAiAgreementIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AgreementSuggester suggester;

    @Test
    void estimateWithAnAiSuggestedAgreementBelowHighHasLowConfidence() throws Exception {
        when(suggester.suggest(eq("Lovisenberg Diakonale"), any(), anyList()))
                .thenReturn(new AgreementSuggestion("oslo-kommune", ConfidenceLevel.MEDIUM, "In Oslo."));

        mvc.perform(post("/api/estimates").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"employerName\":\"Lovisenberg Diakonale\",\"jobCategory\":\"nurse\",\"positionPercent\":100,"
                                + "\"currentAnnualSalary\":600000,\"experiences\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreementSuggestion.source").value("ai"))
                .andExpect(jsonPath("$.agreementSuggestion.confidence").value("medium"))
                .andExpect(jsonPath("$.agreementSuggestion.assumed").value(true))
                .andExpect(jsonPath("$.confidence.level").value("low"))
                .andExpect(jsonPath("$.confidence.reasons", hasItem(containsString("foreslått av KI"))));
    }
}
