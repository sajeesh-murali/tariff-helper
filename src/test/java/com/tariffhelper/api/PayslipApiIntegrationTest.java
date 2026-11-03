package com.tariffhelper.api;

import com.tariffhelper.TestImages;
import com.tariffhelper.TestPdf;
import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import com.tariffhelper.ocr.OcrEngine;
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
@SpringBootTest(properties = {"app.rate-limit.per-client-per-minute=1000", "app.payslip.ai-mode=missing"})
@AutoConfigureMockMvc
class PayslipApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PayslipFieldExtractor extractor;

    @MockitoBean
    AgreementSuggester suggester;

    @MockitoBean
    OcrEngine ocr;

    private ResultActions upload(byte[] bytes) throws Exception {
        return upload(bytes, "payslip.pdf", "application/pdf");
    }

    private ResultActions upload(byte[] bytes, String name, String contentType) throws Exception {
        return mvc.perform(multipart("/api/payslips/parse")
                .file(new MockMultipartFile("file", name, contentType, bytes)));
    }

    /**
     * A payslip written in words the rules do not know ("Arbeidsgiverinfo", "Kode for stillingen"): the excerpt still
     * has the keywords, so the model is asked. The values themselves are only in sentences, not under a known title.
     */
    private static final List<String> HARD_LINES = List.of(
            "Lønnsslipp august 2026",
            "Navn: Kari Testesen",
            "Fødselsnummer: 01019012345",
            "Kontonummer: 4202.15.12345",
            "Arbeidsgiverinfo: Oslo kommune, Bydel Vestre Aker",
            "Din stilling er sykepleier i 80 prosent",
            "Kode for stillingen: 7936",
            "Skattetrekk 12 345,00");

    private static byte[] hardPayslip() {
        return TestPdf.withLines(HARD_LINES);
    }

    private static String sampleText() {
        return String.join(System.lineSeparator(), TestPdf.SAMPLE_PAYSLIP_LINES);
    }

    @Test
    void aClearlyLabelledPayslipIsReadByTheRulesAloneAndNothingIsSentToTheModel() throws Exception {
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
                .andExpect(jsonPath("$.fieldStatus.employerName").value("rules"))
                .andExpect(jsonPath("$.fieldStatus.annualSalaryFullTime").value("rules"))
                .andExpect(jsonPath("$.fieldStatus.stillingskode").value("rules"))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("Check them")));
        verify(suggester, never()).suggest(any(), any(), anyList());
        verify(extractor, never()).extract(anyString());
    }

    @Test
    void theModelIsOnlyAskedForWhatTheRulesMissedAndNeverSeesPersonalData() throws Exception {
        when(extractor.extract(anyString())).thenReturn(new PayslipFields("7936", null, null,
                "Oslo kommune", "Sykepleier", 80, null));

        upload(hardPayslip())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employerName").value("Oslo kommune"))
                .andExpect(jsonPath("$.positionPercent").value(80))
                .andExpect(jsonPath("$.stillingskode").value("7936"))
                .andExpect(jsonPath("$.fieldStatus.employerName").value("ai"))
                .andExpect(jsonPath("$.fieldStatus.positionPercent").value("ai"))
                .andExpect(jsonPath("$.fieldStatus.lonnstrinn").value("notFound"))
                .andExpect(jsonPath("$.agreement.agreementId").value("oslo-kommune"));

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(extractor).extract(sent.capture());
        assertThat(sent.getValue())
                .contains("Arbeidsgiverinfo: Oslo kommune")
                .contains("Din stilling er sykepleier i 80 prosent")
                .doesNotContain("Kari").doesNotContain("Testesen")
                .doesNotContain("01019012345").doesNotContain("4202").doesNotContain("Lønnsslipp");
    }

    @Test
    void aValueTheRulesFoundIsNeverReplacedByTheModelsAnswer() throws Exception {
        when(extractor.extract(anyString())).thenReturn(new PayslipFields("1111", 99, YearMonth.of(2001, 1),
                "Annen kommune", "Lege", 50, BigDecimal.valueOf(900000)));
        byte[] partlyLabelled = TestPdf.withLines(List.of("Arbeidsgiver: Oslo kommune", "Din stilling er sykepleier"));

        upload(partlyLabelled)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employerName").value("Oslo kommune"))
                .andExpect(jsonPath("$.fieldStatus.employerName").value("disagree"))
                .andExpect(jsonPath("$.jobTitle").value("Lege"))
                .andExpect(jsonPath("$.fieldStatus.jobTitle").value("ai"));
    }

    private static PayslipFields onlyEmployer(String employer, String jobTitle) {
        return new PayslipFields(null, null, null, employer, jobTitle, null, null);
    }

    @Test
    void anAgreementWithoutRulesIsSuggestedButFlaggedAsNotAvailable() throws Exception {
        when(extractor.extract(anyString())).thenReturn(onlyEmployer("NAV Grünerløkka", "Rådgiver"));

        upload(hardPayslip())
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

        upload(hardPayslip())
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

        upload(hardPayslip())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employerName").value("Coop Norge"))
                .andExpect(jsonPath("$.agreement").doesNotExist());
    }

    @Test
    void noAgreementIsSuggestedWhenTheEmployerWasNotRead() throws Exception {
        when(extractor.extract(anyString())).thenReturn(new PayslipFields("7936", 45, null, null, "Sykepleier",
                null, null));

        upload(hardPayslip())
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
    void aPictureIsReadWithOcrAndNeverPassesPersonalDataOn() throws Exception {
        when(ocr.recognize(any())).thenReturn(String.join("\n", HARD_LINES));
        when(extractor.extract(anyString())).thenReturn(new PayslipFields("7936", 45, YearMonth.of(2015, 8),
                "Oslo kommune", "Sykepleier", 80, BigDecimal.valueOf(612300)));

        upload(TestImages.jpeg(1600, 1200), "photo.jpg", "image/jpeg")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employerName").value("Oslo kommune"))
                .andExpect(jsonPath("$.stillingskode").value("7936"))
                .andExpect(jsonPath("$.agreement.agreementId").value("oslo-kommune"));

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(extractor).extract(sent.capture());
        assertThat(sent.getValue())
                .contains("Arbeidsgiverinfo: Oslo kommune")
                .contains("Kode for stillingen: 7936")
                .doesNotContain("Kari").doesNotContain("Testesen")
                .doesNotContain("01019012345").doesNotContain("4202");
    }

    @Test
    void aPictureWithClearTitlesIsReadByTheRulesWithoutTheModel() throws Exception {
        when(ocr.recognize(any())).thenReturn(sampleText());

        upload(TestImages.jpeg(1600, 1200), "photo.jpg", "image/jpeg")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employerName").value("Oslo kommune"))
                .andExpect(jsonPath("$.lonnstrinn").value(45))
                .andExpect(jsonPath("$.fieldStatus.lonnstrinn").value("rules"));

        verify(extractor, never()).extract(anyString());
    }

    @Test
    void thePictureTypeIsDecidedFromTheFileNotFromItsNameOrDeclaredType() throws Exception {
        when(ocr.recognize(any())).thenReturn(sampleText());
        when(extractor.extract(anyString())).thenReturn(PayslipFields.empty());

        upload(TestImages.png(1200, 800), "payslip.pdf", "application/pdf").andExpect(status().isOk());

        verify(ocr).recognize(any());
    }

    @Test
    void aScannedPdfWithoutTextIsReadWithOcr() throws Exception {
        when(ocr.recognize(any())).thenReturn(sampleText());
        when(extractor.extract(anyString())).thenReturn(PayslipFields.empty());

        upload(TestPdf.pages(1, List.of())).andExpect(status().isOk());

        verify(ocr).recognize(any());
    }

    @Test
    void aPictureThatCannotBeReadIsRejectedWithoutEchoingIt() throws Exception {
        when(ocr.recognize(any())).thenThrow(new InvalidRequestException("The picture could not be read."));

        upload(TestImages.png(1200, 800), "photo.png", "image/png")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The picture could not be read."));

        verify(extractor, never()).extract(anyString());
    }

    @Test
    void aFileThatIsNeitherAPdfNorAPictureIsRejectedWithoutEchoingIt() throws Exception {
        upload("Kari Testesen 01019012345".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file must be a PDF, JPG, PNG, WebP, TIFF, HEIC or HEIF."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Kari"))));

        verify(extractor, never()).extract(anyString());
        verify(ocr, never()).recognize(any());
    }

    @Test
    void aProtectedPdfAsksForThePasswordAndNothingReachesTheModel() throws Exception {
        byte[] protectedPdf = TestPdf.withPassword(TestPdf.samplePayslip(), "hemmelig");

        upload(protectedPdf)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("password protected")));

        verify(extractor, never()).extract(anyString());
    }

    @Test
    void aProtectedPdfIsReadWhenThePasswordIsSent() throws Exception {
        when(extractor.extract(anyString())).thenReturn(new PayslipFields("7936", 45, YearMonth.of(2015, 8),
                "Oslo kommune", "Sykepleier", 80, BigDecimal.valueOf(612300)));
        byte[] protectedPdf = TestPdf.withPassword(TestPdf.samplePayslip(), "hemmelig");

        mvc.perform(multipart("/api/payslips/parse")
                        .file(new MockMultipartFile("file", "payslip.pdf", "application/pdf", protectedPdf))
                        .param("password", "hemmelig"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employerName").value("Oslo kommune"));
    }

    @Test
    void aWrongPasswordIsRejectedAndNeverEchoed() throws Exception {
        byte[] protectedPdf = TestPdf.withPassword(TestPdf.samplePayslip(), "hemmelig");

        mvc.perform(multipart("/api/payslips/parse")
                        .file(new MockMultipartFile("file", "payslip.pdf", "application/pdf", protectedPdf))
                        .param("password", "feil-passord"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The password is wrong."))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("feil-passord"))));
    }

    @Test
    void aTooLongPasswordIsRejected() throws Exception {
        mvc.perform(multipart("/api/payslips/parse")
                        .file(new MockMultipartFile("file", "payslip.pdf", "application/pdf", TestPdf.samplePayslip()))
                        .param("password", "x".repeat(PayslipController.MAX_PASSWORD_CHARS + 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPdfOverTwoMegabytesIsRejected() throws Exception {
        byte[] bigPdf = new byte[(int) PayslipController.MAX_PDF_BYTES + 1];
        System.arraycopy("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), 0, bigPdf, 0, 8);

        upload(bigPdf).andExpect(status().isPayloadTooLarge());

        verify(extractor, never()).extract(anyString());
    }

    @Test
    void aPictureOverEightMegabytesIsRejected() throws Exception {
        upload(new byte[(int) PayslipController.MAX_IMAGE_BYTES + 1], "photo.jpg", "image/jpeg")
                .andExpect(status().isPayloadTooLarge());

        verify(ocr, never()).recognize(any());
    }

    @Test
    void aMissingFileIsABadRequest() throws Exception {
        mvc.perform(multipart("/api/payslips/parse")).andExpect(status().isBadRequest());
    }
}
