package com.tariffhelper.service;

import com.tariffhelper.TestPdf;
import com.tariffhelper.agreement.AgreementRepository;
import com.tariffhelper.api.dto.response.SuggestResponse;
import com.tariffhelper.domain.PayslipFields;
import com.tariffhelper.llm.PayslipFieldExtractor;
import com.tariffhelper.service.PayslipProperties.AiMode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The rules read the payslip, the model reads the excerpt, and the two readings are compared. */
class PayslipServiceTest {

    /** What the rules read from {@link TestPdf#SAMPLE_PAYSLIP_LINES}. */
    private static final PayslipFields RULES_READING = new PayslipFields("7936", 45, YearMonth.of(2015, 8),
            "Oslo kommune", "Sykepleier", 80, BigDecimal.valueOf(612300));

    private final PayslipFileReader reader = mock(PayslipFileReader.class);
    private final PayslipFieldExtractor extractor = mock(PayslipFieldExtractor.class);
    private final AgreementSuggestionService suggestions = mock(AgreementSuggestionService.class);
    private final AgreementRepository agreements = mock(AgreementRepository.class);

    private PayslipService service(AiMode mode, String payslipText) {
        when(reader.read(any(), any())).thenReturn(payslipText);
        when(suggestions.suggest(any(), any())).thenReturn(SuggestResponse.none());
        return new PayslipService(reader, new PayslipRuleReader(), new PayslipRedactor(), extractor, suggestions,
                agreements, new PayslipProperties(mode));
    }

    private static String clearPayslip() {
        return String.join("\n", TestPdf.SAMPLE_PAYSLIP_LINES);
    }

    private PayslipService.Result parse(AiMode mode, String text) {
        return service(mode, text).parse(new byte[] {1});
    }

    @Test
    void alwaysModeAsksTheModelEvenWhenTheRulesFoundEverythingAndMarksAgreementAsConfirmed() {
        when(extractor.extract(anyString())).thenReturn(RULES_READING);

        PayslipService.Result result = parse(AiMode.ALWAYS, clearPayslip());

        verify(extractor, times(1)).extract(anyString());
        assertThat(result.fields()).isEqualTo(RULES_READING);
        assertThat(result.fieldStatus().values()).hasSize(7).containsOnly(PayslipService.CONFIRMED);
    }

    @Test
    void whenTheTwoReadingsDifferTheRulesValueIsShownAndTheFieldIsFlagged() {
        when(extractor.extract(anyString())).thenReturn(new PayslipFields("7936", 46, YearMonth.of(2015, 9),
                "Oslo kommune", "Sykepleier", 60, BigDecimal.valueOf(612300)));

        PayslipService.Result result = parse(AiMode.ALWAYS, clearPayslip());

        assertThat(result.fields()).isEqualTo(RULES_READING);
        assertThat(result.fieldStatus())
                .containsEntry("positionPercent", PayslipService.DISAGREE)
                .containsEntry("lonnstrinn", PayslipService.DISAGREE)
                .containsEntry("ansiennitetsdato", PayslipService.DISAGREE)
                .containsEntry("employerName", PayslipService.CONFIRMED)
                .containsEntry("stillingskode", PayslipService.CONFIRMED)
                .containsEntry("annualSalaryFullTime", PayslipService.CONFIRMED);
    }

    @Test
    void whatTheRulesMissedIsTakenFromTheModel() {
        String text = "Din stilling er sykepleier i 80 prosent\nArbeidsgiverinfo: Oslo kommune, Bydel Vestre Aker";
        when(extractor.extract(anyString())).thenReturn(new PayslipFields(null, null, null, "Oslo kommune",
                "Sykepleier", 80, null));

        PayslipService.Result result = parse(AiMode.ALWAYS, text);

        assertThat(result.fields().employerName()).isEqualTo("Oslo kommune");
        assertThat(result.fields().positionPercent()).isEqualTo(80);
        assertThat(result.fieldStatus()).containsEntry("employerName", "ai").containsEntry("positionPercent", "ai")
                .containsEntry("annualSalaryFullTime", "notFound");
    }

    @Test
    void ifTheModelFindsNothingEveryRulesValueStaysWithoutAConfirmation() {
        when(extractor.extract(anyString())).thenReturn(PayslipFields.empty());

        PayslipService.Result result = parse(AiMode.ALWAYS, clearPayslip());

        assertThat(result.fields()).isEqualTo(RULES_READING);
        assertThat(result.fieldStatus().values()).containsOnly("rules");
    }

    @Test
    void missingModeOnlyAsksTheModelWhenTheRulesLeftAGap() {
        PayslipService.Result complete = parse(AiMode.MISSING, clearPayslip());
        verify(extractor, never()).extract(anyString());
        assertThat(complete.fieldStatus().values()).containsOnly("rules");

        when(extractor.extract(anyString())).thenReturn(PayslipFields.empty());
        parse(AiMode.MISSING, "Din stilling er sykepleier i 80 prosent");
        verify(extractor, times(1)).extract(anyString());
    }

    @Test
    void offModeNeverSendsAnythingToTheModel() {
        PayslipService.Result result = parse(AiMode.OFF, "Din stilling er sykepleier i 80 prosent");

        verify(extractor, never()).extract(anyString());
        assertThat(result.fields().foundCount()).isZero();
    }

    @Test
    void theModelOnlyEverSeesTheRedactedExcerpt() {
        when(extractor.extract(anyString())).thenReturn(PayslipFields.empty());
        String text = clearPayslip() + "\nNavn: Kari Testesen\nFødselsnummer: 01019012345";

        parse(AiMode.ALWAYS, text);

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(extractor).extract(sent.capture());
        assertThat(sent.getValue()).contains("Arbeidsgiver: Oslo kommune")
                .doesNotContain("Kari").doesNotContain("Testesen").doesNotContain("01019012345");
    }

    @Test
    void aRowTheRulesReadWithoutAnyTitleStillReachesTheModel() {
        when(extractor.extract(anyString())).thenReturn(PayslipFields.empty());
        String text = String.join("\n", "Testby kommune", "Sum brutto lønn 44 195,67",
                "123456          Pleiemedarbeider          87,50          01.03.2021", "NETTO UTBETALT 31 113,76");

        PayslipService.Result result = parse(AiMode.ALWAYS, text);

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(extractor).extract(sent.capture());
        assertThat(sent.getValue()).contains("Pleiemedarbeider").doesNotContain("NETTO UTBETALT");
        assertThat(result.fields().jobTitle()).isEqualTo("Pleiemedarbeider");
    }

    @Test
    void namesMatchWhenOneContainsTheOtherButCodesMustBeEqual() {
        assertThat(PayslipService.same("Oslo kommune", "oslo  kommune")).isTrue();
        assertThat(PayslipService.same("Eksempel kommune (fiktiv)", "Eksempel kommune")).isTrue();
        assertThat(PayslipService.same("Oslo kommune", "Bergen kommune")).isFalse();
        assertThat(PayslipService.same("7136", "136")).isFalse();
        assertThat(PayslipService.same(80, 80)).isTrue();
        assertThat(PayslipService.same(BigDecimal.valueOf(612300), new BigDecimal("612300.00"))).isTrue();
        assertThat(PayslipService.same(YearMonth.of(2015, 8), YearMonth.of(2015, 9))).isFalse();
    }

    @Test
    void theStatusListCoversEveryFieldInOrder() {
        when(extractor.extract(anyString())).thenReturn(PayslipFields.empty());

        Map<String, String> status = parse(AiMode.ALWAYS, "Sum brutto 1 000,00").fieldStatus();

        assertThat(List.copyOf(status.keySet())).isEqualTo(PayslipFields.FIELD_NAMES);
    }

    private static final String OSLO_HEADER = "Oslo kommune\nStilling Stillingskode - Tittel St-% Ansiennitet L-ramme - Alt Ltr Orgenhet\n";

    @Test
    void theAlternativIsNotKeptAsTheLonnstrinnWhateverReadIt() {
        // The step under Ltr was lost in the text, so only "026 - 8" is left. Neither the rules nor the model may
        // pass the 8 on as the lønnstrinn; the person fills it in.
        String lost = OSLO_HEADER + "S160076 0058000 - Sykepleier 0 01.12.2024 026 - 8 Hjemmetjeneste";
        when(extractor.extract(anyString())).thenReturn(new PayslipFields(null, 8, null, null, null, null, null));

        PayslipService.Result result = parse(AiMode.ALWAYS, lost);

        assertThat(result.fields().lonnstrinn()).isNull();
        assertThat(result.fieldStatus().get("lonnstrinn")).isEqualTo("notFound");
        assertThat(result.fields().ansiennitetsdato()).isEqualTo(YearMonth.of(2024, 12));
    }

    /** A service with the real agreement files and an Oslo kommune suggestion, for the pay table lookup. */
    private PayslipService.Result parseInOslo(String text) {
        AgreementRepository real = new AgreementRepository();
        when(reader.read(any(), any())).thenReturn(text);
        when(suggestions.suggest(any(), any())).thenReturn(new SuggestResponse(List.of(
                new SuggestResponse.Suggestion("oslo-kommune", com.tariffhelper.domain.ConfidenceLevel.HIGH, "test",
                        null)), com.tariffhelper.domain.SuggestionSource.RULES));
        when(agreements.findById("oslo-kommune")).thenReturn(real.findById("oslo-kommune"));
        return new PayslipService(reader, new PayslipRuleReader(), new PayslipRedactor(), extractor, suggestions,
                agreements, new PayslipProperties(AiMode.OFF)).parse(new byte[] {1});
    }

    @Test
    void aZeroPercentPositionInOsloGetsItsFullTimeSalaryFromTheLonnstrinnTable() {
        String slip = OSLO_HEADER + "S160076 0058000 - Sykepleier 0 01.12.2024 026 - 8 31 Hjemmetjeneste\n";

        PayslipService.Result result = parseInOslo(slip);

        assertThat(result.fields().positionPercent()).isZero();
        assertThat(result.fields().lonnstrinn()).isEqualTo(31);
        assertThat(result.fields().annualSalaryFullTime()).isEqualByComparingTo("606200");
        assertThat(result.fieldStatus()).containsEntry("annualSalaryFullTime", PayslipService.FROM_TABLE);
    }

    @Test
    void theTableIsNotUsedWhenThePositionIsAboveZeroOrTheStepIsMissing() {
        String withPercent = OSLO_HEADER + "S160076 0058000 - Sykepleier 60 01.12.2024 026 - 8 31 Hjemmetjeneste\n";
        String withoutStep = OSLO_HEADER + "S160076 0058000 - Sykepleier 0 01.12.2024 Hjemmetjeneste\n";

        assertThat(parseInOslo(withPercent).fields().annualSalaryFullTime()).isNull();
        assertThat(parseInOslo(withoutStep).fields().annualSalaryFullTime()).isNull();
    }

    @Test
    void aSalaryPrintedOnTheSlipIsNeverReplacedByTheTable() {
        String slip = OSLO_HEADER + "S160076 0058000 - Sykepleier 0 01.12.2024 026 - 8 31 Hjemmetjeneste\n"
                + "Årslønn 100 % 612 300,00\n";

        PayslipService.Result result = parseInOslo(slip);

        assertThat(result.fieldStatus().get("annualSalaryFullTime")).isNotEqualTo(PayslipService.FROM_TABLE);
        assertThat(result.fields().annualSalaryFullTime()).isNotEqualByComparingTo("606200");
    }

    @Test
    void aYearlySalaryWorkedOutFromAMonthlySalaryIsMarkedAsCalculated() {
        String slip = "Oslo kommune\nStillingsprosent 100\n1000 Månedslønn  1,00  41 466,67\n";

        PayslipService.Result result = parse(AiMode.OFF, slip);

        assertThat(result.fields().annualSalaryFullTime()).isEqualByComparingTo("497600");
        assertThat(result.fieldStatus()).containsEntry("annualSalaryFullTime", PayslipService.CALCULATED);
    }

    @Test
    void thePayFrameIsNotKeptAsTheLonnstrinnEither() {
        // "026 - 8" is the pay frame and the alternativ; the step after it was lost, so 26 must not become the step.
        String lost = OSLO_HEADER + "S160076 0058000 - Sykepleier 0 01.12.2024 026 - 8 Hjemmetjeneste";
        when(extractor.extract(anyString())).thenReturn(new PayslipFields(null, 26, null, null, null, null, null));

        PayslipService.Result result = parse(AiMode.ALWAYS, lost);

        assertThat(result.fields().lonnstrinn()).isNull();
    }

    @Test
    void theRealStepNextToTheAlternativIsKept() {
        String whole = OSLO_HEADER + "S160076 0058000 - Sykepleier 0 01.12.2024 026 - 8 31 Hjemmetjeneste";
        when(extractor.extract(anyString())).thenReturn(new PayslipFields(null, 31, null, null, null, null, null));

        assertThat(parse(AiMode.ALWAYS, whole).fields().lonnstrinn()).isEqualTo(31);
    }
}
