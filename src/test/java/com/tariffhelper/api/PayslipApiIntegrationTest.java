package com.tariffhelper.api;

import com.tariffhelper.TestPdf;
import com.tariffhelper.domain.PayslipFields;
import com.tariffhelper.domain.ConfidenceLevel;
import com.tariffhelper.llm.AgreementSuggester;
import com.tariffhelper.llm.AgreementSuggestion;
import com.tariffhelper.llm.PayslipFieldExtractor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The extractor (the LLM call) is mocked, so no external calls are made. */
@SpringBootTest(properties = "app.rate-limit.per-client-per-minute=1000")
@AutoConfigureMockMvc
class PayslipApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PayslipFieldExtractor extractor;

    @MockitoBean
    AgreementSuggester suggester;

    private ResultActions upload(byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/payslips/parse")
                .file(new MockMultipartFile("file", "payslip.pdf", "application/pdf", bytes)));
    }

    @Test
    void returnsTheExtractedValuesAndNeverPassesPersonalDataOn() throws Exception {
        when(extractor.extract(anyString())).thenReturn(new PayslipFields("7936", 45, YearMonth.of(2015, 8),
                "Oslo kommune", "Sykepleier", 80, BigDecimal.valueOf(612300)));

        upload(TestPdf.samplePayslip())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employerName").value("Oslo kommune"))
                .andExpect(jsonPath("$.jobTitle").value("Sykepleier"))
                .andExpect(jsonPath("$.positionPercent").value(80))
                .andExpect(jsonPath("$.annualSalaryFullTime").value(612300))
                .andExpect(jsonPath("$.currentAnnualSalary").value(489840))
                .andExpect(jsonPath("$.stillingskode").value("7936"))
                .andExpect(jsonPath("$.lonnstrinn").value(45))
                .andExpect(jsonPath("$.ansiennitetsdato").value("2015-08"))
                .andExpect(jsonPath("$.agreement.agreementId").value("oslo-kommune"))
                .andExpect(jsonPath("$.agreement.name").isNotEmpty())
                .andExpect(jsonPath("$.agreement.status").value("available"))
                .andExpect(jsonPath("$.agreement.source").value("rules"))
                .andExpect(jsonPath("$.agreement.confidence").value("high"))
                .andExpect(jsonPath("$.agreement.reason").isNotEmpty())
                .andExpect(jsonPath("$.agreement.jobCategory").value("nurse"))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("Check them")));
        verify(suggester, never()).suggest(any(), any(), anyList());

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(extractor).extract(sent.capture());
        assertThat(sent.getValue())
                .contains("Arbeidsgiver: Oslo kommune")
                .contains("Stillingsprosent: 80 %")
                .contains("Årslønn i 100 % stilling før skatt 612 300,00")
                .contains("Registrert ansiennitet: 01.08.2015")
                .contains("7936 Sykepleier 45 01.08.2015")
                .doesNotContain("Kari").doesNotContain("Testesen")
                .doesNotContain("01019012345").doesNotContain("4202").doesNotContain("Lønnsslipp");
    }

    private static PayslipFields onlyEmployer(String employer, String jobTitle) {
        return new PayslipFields(null, null, null, employer, jobTitle, null, null);
    }

    @Test
    void anAgreementWithoutRulesIsSuggestedButFlaggedAsNotAvailable() throws Exception {
        when(extractor.extract(anyString())).thenReturn(onlyEmployer("NAV Grünerløkka", "Rådgiver"));

        upload(TestPdf.samplePayslip())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreement.agreementId").value("staten"))
                .andExpect(jsonPath("$.agreement.status").value("placeholder"))
                .andExpect(jsonPath("$.agreement.unavailableReason").isNotEmpty());
    }

    @Test
    void whenTheRulesCannotDecideTheAiChoosesOnlyFromTheKnownAgreements() throws Exception {
        when(extractor.extract(anyString())).thenReturn(onlyEmployer("Lovisenberg Diakonale", null));
        when(suggester.suggest(eq("Lovisenberg Diakonale"), isNull(), anyList()))
                .thenReturn(new AgreementSuggestion("spekter", ConfidenceLevel.MEDIUM, "A private hospital."));

        upload(TestPdf.samplePayslip())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreement.agreementId").value("spekter"))
                .andExpect(jsonPath("$.agreement.source").value("ai"))
                .andExpect(jsonPath("$.agreement.confidence").value("medium"))
                .andExpect(jsonPath("$.agreement.reason").value("A private hospital."));
        verify(suggester).suggest(eq("Lovisenberg Diakonale"), isNull(),
                eq(java.util.List.of("ks-hta", "oslo-kommune", "spekter", "staten")));
    }

    @Test
    void noAgreementIsSuggestedWhenNeitherTheRulesNorTheAiCanDecide() throws Exception {
        when(extractor.extract(anyString())).thenReturn(onlyEmployer("Coop Norge", "Assistent"));
        when(suggester.suggest(any(), any(), anyList())).thenReturn(AgreementSuggestion.unknown());

        upload(TestPdf.samplePayslip())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employerName").value("Coop Norge"))
                .andExpect(jsonPath("$.agreement").doesNotExist());
    }

    @Test
    void noAgreementIsSuggestedWhenTheEmployerWasNotRead() throws Exception {
        when(extractor.extract(anyString())).thenReturn(new PayslipFields("7936", 45, null, null, "Sykepleier",
                null, null));

        upload(TestPdf.samplePayslip())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreement").doesNotExist());

        verify(suggester, never()).suggest(any(), any(), anyList());
    }

    @Test
    void aPayslipWithNoRecognisedLabelsSendsNothingToTheModel() throws Exception {
        upload(TestPdf.withLines(List.of("Kari Testesen", "Utbetalt 30 000,00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stillingskode").doesNotExist())
                .andExpect(jsonPath("$.lonnstrinn").doesNotExist())
                .andExpect(jsonPath("$.ansiennitetsdato").doesNotExist());

        verify(extractor, never()).extract(anyString());
    }

    @Test
    void aFileThatIsNotAPdfIsRejectedWithoutEchoingIt() throws Exception {
        upload("Kari Testesen 01019012345".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is not a PDF."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Kari"))));

        verify(extractor, never()).extract(anyString());
    }

    @Test
    void aScannedPdfWithoutTextIsRejected() throws Exception {
        upload(TestPdf.pages(1, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Scanned")));
    }

    @Test
    void aTooLargeFileIsRejected() throws Exception {
        upload(new byte[(int) PayslipController.MAX_BYTES + 1]).andExpect(status().isPayloadTooLarge());

        verify(extractor, never()).extract(anyString());
    }

    @Test
    void aMissingFileIsABadRequest() throws Exception {
        mvc.perform(multipart("/api/payslips/parse")).andExpect(status().isBadRequest());
    }
}
