package com.tariffhelper.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClientConfigApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void givesTheBookingLinkFromTheSettings() throws Exception {
        mvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingUrl").value(org.hamcrest.Matchers.startsWith("https://calendly.com/")));
    }

    @Test
    void aMissingOrNonHttpsLinkMeansNoBooking() {
        assertThat(new ClientConfigController("").get().bookingUrl()).isNull();
        assertThat(new ClientConfigController("  ").get().bookingUrl()).isNull();
        assertThat(new ClientConfigController("javascript:alert(1)").get().bookingUrl()).isNull();
        assertThat(new ClientConfigController("http://calendly.com/x").get().bookingUrl()).isNull();
        assertThat(new ClientConfigController(" https://calendly.com/x ").get().bookingUrl())
                .isEqualTo("https://calendly.com/x");
    }
}
