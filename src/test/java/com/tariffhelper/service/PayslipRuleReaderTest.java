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
                new Expected("Oslo style: titles over values, hourly pay, 0 % is kept as 0", fixture("oslo-style-hourly.txt"),
                        fields("0058000", 31, YearMonth.of(2020, 12), "Oslo kommune", "Sykepleier", 0, null),
                        List.of("annualSalaryFullTime")),
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
    void anOsloRowWithZeroPercentAndAStepGivesBothValues() {
        // Under "St-%" the row prints 0 (called in for single shifts) and under "Ltr" it prints 31.
        String text = "Oslo kommune\n"
                + "Stilling   Stillingskode - Tittel         St-%   Ansiennitet   L-ramme - Alt   Ltr   Orgenhet\n"
                + "|  S000001  |  0058000 -  Sykepleier  |   0   |  01.12.2024  |  0260«- «8  |  31  |  Hjemmetjeneste\n";

        PayslipFields fields = new PayslipRuleReader().read(text).fields();

        assertThat(fields.positionPercent()).isZero();
        assertThat(fields.lonnstrinn()).isEqualTo(31);
        assertThat(fields.ansiennitetsdato()).isEqualTo(YearMonth.of(2024, 12));
    }

    @Test
    void aPictureWithDriftedColumnsStillGivesZeroPercentAndTheStep() {
        // As read from a photographed Oslo slip: the 0 sits left of the St-% title, the pair is misread.
        String text = "Oslo kommune\n"
                + "[_ stiting |                  = Tittel               | st-% | Ansiennitet |L-ramme - Alt [Ltr |      Orgenhet\n"
                + "|   S000001   0058000 - Sykepleier      |     0     |   01.12.2024   |    0260«- «8  |  31   |    Hjemmetjeneste\n";

        PayslipFields fields = new PayslipRuleReader().read(text).fields();

        assertThat(fields.positionPercent()).isZero();
        assertThat(fields.lonnstrinn()).isEqualTo(31);
        // Oslo's seven-digit code is shown exactly as printed, even when the title cell was misread
        assertThat(fields.stillingskode()).isEqualTo("0058000");
        assertThat(fields.jobTitle()).isEqualTo("Sykepleier");
    }

    private static java.math.BigDecimal monthlySalary(String... lines) {
        return new PayslipRuleReader().read(String.join("\n", lines)).fields().annualSalaryFullTime();
    }

    @Test
    void aMonthlySalaryGivesTheYearlySalaryWhenTheSlipPrintsNoYearlyOne() {
        // two amounts on the line: the basis for a full position and the amount paid at 91,62 %
        PayslipRuleReader.Reading reading = new PayslipRuleReader().read(String.join("\n",
                "Teststad kommune",
                "St.kode         Betegnelse            St-%      LR     Ansiennitet",
                "658300          Pleiemedarbeider      91,62     4.1    01.02.2022",
                "1100 | Månedslønn (3003)                       34 983,33          32 051,73",
                "1100E | Etterbetaling månedslønn (3003)        1 000,00"));

        assertThat(reading.fields().positionPercent()).isEqualTo(92);
        assertThat(reading.fields().annualSalaryFullTime()).isEqualByComparingTo("419800");
        assertThat(reading.calculated()).containsExactly("annualSalaryFullTime");
    }

    @Test
    void variousWaysOfWritingAMonthlySalaryAreRecognised() {
        String percent = "Stillingsprosent 100";
        assertThat(monthlySalary(percent, "Månedslønn: 41 466,67")).isEqualByComparingTo("497600");
        assertThat(monthlySalary(percent, "Fast månedslønn kr 41 466,67")).isEqualByComparingTo("497600");
        assertThat(monthlySalary(percent, "1100 Manedslonn 41 466,67")).isEqualByComparingTo("497600"); // å and ø lost
        assertThat(monthlySalary(percent, "Fastlønn (månedlig)      41 466,67")).isEqualByComparingTo("497600");
        assertThat(monthlySalary(percent, "Lønn pr. måned 41 466,67")).isEqualByComparingTo("497600");
        assertThat(monthlySalary(percent, "Monthly salary 41 466,67")).isEqualByComparingTo("497600");
        assertThat(monthlySalary(percent, "1000 Månedslønn  1,00  41466,67")).isEqualByComparingTo("497600");
        // a share of the full position: what is paid at 50 % is half of the full-time month
        assertThat(monthlySalary("Stillingsprosent 50", "Månedslønn 20 733,33")).isEqualByComparingTo("497600");
    }

    @Test
    void aMonthlySalaryIsStillFoundWhenTextRecognitionMisreadsTheTitleOrTheDecimals() {
        String percent = "Stillingsprosent 100";
        assertThat(monthlySalary(percent, "1100 Månedslann (3003)   41 466,67")).isEqualByComparingTo("497600");
        assertThat(monthlySalary(percent, "1100 Manedsionn (3003)   41 466,67")).isEqualByComparingTo("497600");
        assertThat(monthlySalary(percent, "11OO Månedslønn   41 466,67")).isEqualByComparingTo("497600");
        assertThat(monthlySalary(percent, "Månedslønn   41 466.67")).isEqualByComparingTo("497600");
        assertThat(monthlySalary(percent, "Månedslønn   41.466,67.")).isEqualByComparingTo("497600");
        // other words that only look a little like a monthly title are not one
        assertThat(monthlySalary(percent, "2000 Timelønn (3003)   41 466,67")).isNull();
        assertThat(monthlySalary(percent, "2102 Overtidslønn   41 466,67")).isNull();
        assertThat(monthlySalary(percent, "1100 Grunnlønn   41 466,67")).isNull();
    }

    @Test
    void aMonthlySalaryIsNotCalculatedWhenItCouldBeWrong() {
        // no position percentage: the amount could be the full-time month or the part-time one
        assertThat(monthlySalary("Månedslønn 41 466,67")).isNull();
        // no position at all (0 %): there is no fixed pay to scale up
        assertThat(monthlySalary("Stillingsprosent 0", "Månedslønn 41 466,67")).isNull();
        // two amounts that do not fit the percentage: nothing says which one is the month's pay
        assertThat(monthlySalary("Stillingsprosent 50", "Månedslønn 34 983,33   32 051,73")).isNull();
        // other pay lines that mention a monthly salary are not the monthly salary
        assertThat(monthlySalary("Stillingsprosent 100", "1100E Etterbetaling månedslønn (3003) 5 000,00",
                "1842 Ferietrekk totalt - fastlønn juni (3003) 3 000,00", "6000 Trekk - timer fastlønn 4 000,00")).isNull();
        // a period or a pay code is not an amount
        assertThat(monthlySalary("Stillingsprosent 100", "1000 Månedslønn 202610 1,00")).isNull();
    }

    @Test
    void aPrintedYearlySalaryIsUsedBeforeAnyMonthlyOne() {
        assertThat(monthlySalary("Stillingsprosent 100", "Årslønn 560 000,00", "Månedslønn 41 466,67"))
                .isEqualByComparingTo("560000");
    }

    @Test
    void anOsloRowIsReadFromTheRowItselfWhenTheTitlesAboveItAreGarbled() {
        // "St-%" came out as "$t-%" and "L-ramme - Alt" lost its spaces, but the row itself is whole.
        String text = "Oslo kommune\n"
                + "Stilling _Stillingskode - Tittel                $t-% - Ansiennitet L-ramme-Alt Ltr      Orgenhet\n"
                + "S000001 � 0058000 -       Sykepleier          0       01.12.2020      026     - 8 31      Hjemmetjeneste\n";

        PayslipFields fields = new PayslipRuleReader().read(text).fields();

        assertThat(fields.positionPercent()).isZero();
        assertThat(fields.lonnstrinn()).isEqualTo(31);
        assertThat(fields.ansiennitetsdato()).isEqualTo(YearMonth.of(2020, 12));
        assertThat(fields.stillingskode()).isEqualTo("0058000");
        assertThat(fields.jobTitle()).isEqualTo("Sykepleier");
    }

    @Test
    void aSevenDigitCodeIsOnlyReadWhenItsLastThreeDigitsAreZero() {
        PayslipRuleReader reader = new PayslipRuleReader();

        assertThat(reader.read("Stillingskode 0059000\n").fields().stillingskode()).isEqualTo("0059000");
        assertThat(reader.read("Stillingskode 7936000\n").fields().stillingskode()).isEqualTo("7936000");
        assertThat(reader.read("Stillingskode 0058123\n").fields().stillingskode()).isNull();
        assertThat(reader.read("Stillingskode 0000000\n").fields().stillingskode()).isNull();
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
    void aSevenDigitCodeIsKeptAndAPayFrameIsNotTakenForThePayStep() {
        String text = "Stillingskode 0058000 Lønnsramme 24\nLR 4.1\n";

        PayslipFields fields = reader.read(text).fields();

        assertThat(fields.stillingskode()).isEqualTo("0058000");
        assertThat(fields.lonnstrinn()).isNull();
    }

    @Test
    void aWrongLetterInATitleStillFindsTheValue() {
        PayslipFields fields = reader.read("Stillingsprosent 60 %\nL0nnstrinn 40\nAnsiennltetsdato 01.02.2018\n").fields();

        assertThat(fields.positionPercent()).isEqualTo(60);
        assertThat(fields.ansiennitetsdato()).isEqualTo(YearMonth.of(2018, 2));
    }
}
