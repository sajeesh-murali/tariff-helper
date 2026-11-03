package com.tariffhelper.service;

import com.tariffhelper.TestPdf;
import com.tariffhelper.domain.PayslipFields;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The payslip layouts seen in practice, written with invented values. Each fixture keeps the spacing of the real text
 * (the picture reader keeps the gaps between columns), so what is tested is how the values are found, not what they
 * are. The last test counts the fields found per layout, so a change that loses one is noticed.
 */
class PayslipRuleReaderTest {

    private final PayslipRuleReader reader = new PayslipRuleReader();

    private static String fixture(String name) {
        try (var in = PayslipRuleReaderTest.class.getResourceAsStream("/payslips/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Expected(String name, String text, PayslipFields fields, List<String> notPrinted) {
    }

    private static PayslipFields fields(String code, Integer trinn, YearMonth date, String employer, String title,
                                        Integer percent, Integer salary) {
        return new PayslipFields(code, trinn, date, employer, title, percent,
                salary == null ? null : BigDecimal.valueOf(salary));
    }

    private static List<Expected> layouts() {
        return List.of(
                new Expected("clean labels, one value per line", fixture("clean-labelled.txt"),
                        fields("7136", 34, YearMonth.of(2019, 9), "Eksempel kommune (fiktiv)", "Helsefagarbeider",
                                100, 497600), List.of()),
                new Expected("labels with colons and a row under a row of titles",
                        String.join("\n", TestPdf.SAMPLE_PAYSLIP_LINES),
                        fields("7936", 45, YearMonth.of(2015, 8), "Oslo kommune", "Sykepleier", 80, 612300), List.of()),
                new Expected("Oslo style: titles over values, hourly pay, 0 % means not given", fixture("oslo-style-hourly.txt"),
                        fields(null, 31, YearMonth.of(2020, 12), "Oslo kommune", "Sykepleier", null, null),
                        List.of("positionPercent", "annualSalaryFullTime")),
                new Expected("boxed table of codes (St.kode, Betegnelse, St-%, LR, Ansiennitet)", fixture("ks-boxed-table.txt"),
                        fields("123456", null, YearMonth.of(2021, 3), "Testby kommune", "Pleiemedarbeider", 88, null),
                        List.of()),
                new Expected("the same table when its titles were not read", fixture("ks-table-header-lost.txt"),
                        fields("123456", null, YearMonth.of(2021, 3), "Testby kommune", "Pleiemedarbeider", 88, null),
                        List.of()),
                new Expected("employer name over a row that also holds a person's name",
                        fixture("private-header-over-values.txt"),
                        fields(null, null, null, "Eksempel Barnehage AS", null, null, null), List.of()));
    }

    @Test
    void everyLayoutGivesExactlyTheFieldsItPrints() {
        List<String> report = new ArrayList<>();
        int found = 0;
        int expectedTotal = 0;
        for (Expected layout : layouts()) {
            PayslipRuleReader.Reading reading = reader.read(layout.text());
            report.add(layout.name() + ": " + reading.fields().foundCount() + " of " + layout.fields().foundCount());
            assertThat(reading.fields()).as(layout.name()).isEqualTo(layout.fields());
            assertThat(reading.notPrinted().keySet()).as(layout.name() + " (not printed)")
                    .containsExactlyInAnyOrderElementsOf(layout.notPrinted());
            found += reading.fields().foundCount();
            expectedTotal += layout.fields().foundCount();
        }
        System.out.println("Payslip layouts: " + found + " of " + expectedTotal + " printed fields found\n  "
                + String.join("\n  ", report));
        assertThat(found).isEqualTo(expectedTotal);
    }

    @Test
    void personalNumbersAndNamesAreNeverTakenForAValue() {
        for (Expected layout : layouts()) {
            PayslipFields fields = reader.read(layout.text()).fields();
            String all = fields.toString();
            assertThat(all).as(layout.name()).doesNotContain("Testesen").doesNotContain("01010100000")
                    .doesNotContain("Kari").doesNotContain("Testveien");
        }
    }

    @Test
    void aPayslipWithoutAnyLabelGivesNothing() {
        PayslipRuleReader.Reading reading = reader.read("Sum brutto lønn 44 195,67\nNETTO UTBETALT 31 113,76\n");

        assertThat(reading.fields().foundCount()).isZero();
        assertThat(reading.notPrinted()).isEmpty();
    }

    @Test
    void blankTextGivesNothing() {
        assertThat(reader.read(null).fields().foundCount()).isZero();
        assertThat(reader.read("  \n ").fields().foundCount()).isZero();
    }

    @Test
    void aSevenDigitCodeAndAPayFrameAreNotTakenForTheCodeOrThePayStep() {
        String text = "Stillingskode 0058000 Lønnsramme 24\nLR 4.1\n";

        PayslipFields fields = reader.read(text).fields();

        assertThat(fields.stillingskode()).isNull();
        assertThat(fields.lonnstrinn()).isNull();
    }

    @Test
    void aWrongLetterInATitleStillFindsTheValue() {
        PayslipFields fields = reader.read("Stillingsprosent 60 %\nL0nnstrinn 40\nAnsiennltetsdato 01.02.2018\n").fields();

        assertThat(fields.positionPercent()).isEqualTo(60);
        assertThat(fields.ansiennitetsdato()).isEqualTo(YearMonth.of(2018, 2));
    }
}
