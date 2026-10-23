package com.tariffhelper.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.rate-limit.per-client-per-minute=2",
        "app.cors.allowed-origins=https://app.example.com"})
@AutoConfigureMockMvc
class RateLimitAndCorsIntegrationTest {

    private static final String VALID = "{\"agreementId\":\"ks-hta\",\"jobCategory\":\"nurse\",\"positionPercent\":100,"
            + "\"currentAnnualSalary\":500000,\"experiences\":[{\"country\":\"NO\",\"kind\":\"public_service\","
            + "\"startDate\":\"2018-03\",\"endDate\":\"2020-06\"}]}";

    @Autowired
    MockMvc mvc;

    @Test
    void thirdEstimateInAMinuteGets429WithRetryAfter() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/estimates").contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/estimates").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    /** Own client address, so this test does not use up the budget of the test above. */
    @Test
    void suggestEndpointIsLimitedAndSharesTheBudgetWithEstimates() throws Exception {
        String suggest = "{\"employerName\":\"Bergen kommune\"}";
        mvc.perform(post("/api/agreements/suggest").with(from("10.0.0.7"))
                        .contentType(MediaType.APPLICATION_JSON).content(suggest))
                .andExpect(status().isOk());
        mvc.perform(post("/api/estimates").with(from("10.0.0.7"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk());
        mvc.perform(post("/api/agreements/suggest").with(from("10.0.0.7"))
                        .contentType(MediaType.APPLICATION_JSON).content(suggest))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    private static RequestPostProcessor from(String remoteAddr) {
        return request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        };
    }

    @Test
    void otherEndpointsAreNotRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/agreements")).andExpect(status().isOk());
        }
    }

    @Test
    void allowedOriginGetsCorsHeaders() throws Exception {
        mvc.perform(options("/api/estimates")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"));
    }

    @Test
    void otherOriginIsRejected() throws Exception {
        mvc.perform(options("/api/estimates")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
