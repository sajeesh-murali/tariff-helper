package com.tariffhelper.api;

import com.tariffhelper.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StaticSiteIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void homePageIsServed() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    @Test
    void folderPagesAreForwardedToTheirIndexHtml() throws Exception {
        mvc.perform(get("/guider/")).andExpect(forwardedUrl("/guider/index.html"));
        mvc.perform(get("/guider")).andExpect(forwardedUrl("/guider/index.html"));
        mvc.perform(get("/ansiennitet/sykepleier/")).andExpect(forwardedUrl("/ansiennitet/sykepleier/index.html"));
    }

    @Test
    void everyFolderPageHasAnIndexHtml() throws Exception {
        for (String folder : WebConfig.PAGE_FOLDERS) {
            mvc.perform(get("/" + folder + "/index.html")).andExpect(status().isOk());
        }
    }

    @Test
    void homePageLoadsTheScriptThatCallsTheBackend() throws Exception {
        mvc.perform(get("/js/intake-api.js")).andExpect(status().isOk());
        String html = mvc.perform(get("/index.html")).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("/js/intake-api.js");
    }

    /** Both result pages ask Ja/Nei first, and only a yes embeds the calendar in the same page (no jump, no extra button). */
    @Test
    void theResultPagesAskJaOrNeiAndThenEmbedTheCalendarInThePage() throws Exception {
        String script = mvc.perform(get("/js/intake-api.js")).andReturn().getResponse().getContentAsString();
        String card = script.substring(script.indexOf("function referenceCard"), script.indexOf("function resultCard"));
        org.assertj.core.api.Assertions.assertThat(card).contains("bookingCta()");
        String cta = script.substring(script.indexOf("function bookingCta"), script.indexOf("function answerBooking"));
        org.assertj.core.api.Assertions.assertThat(cta).contains("answerBooking('${v}')").contains("answer === 'yes'")
                .contains("booking-frame").doesNotContain("go(4)").doesNotContain("Ja, jeg vil ha en samtale");
        org.assertj.core.api.Assertions.assertThat(cta.indexOf("answer === 'yes'"))
                .isLessThan(cta.indexOf("booking-frame"));
    }

    @Test
    void unknownFolderIsStill404() throws Exception {
        mvc.perform(get("/finnes-ikke/")).andExpect(status().isNotFound());
    }
}
