package com.tariffhelper.service;

import com.tariffhelper.domain.PayslipFields;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cases where the safe answer is an empty field. A wrong seniority date, position or salary gives a wrong estimate
 * without anyone noticing, so each of these must stay empty rather than become a guess.
 */
class PayslipRuleReaderGuardsTest {

    private final PayslipRuleReader reader = new PayslipRuleReader();

    private PayslipFields read(String... lines) {
        return reader.read(String.join("\n", lines)).fields();
    }

    @Test
    void aFractionIsOnlyReadAsAPercentageWhenTheTitleSaysFraction() {
        assertThat(read("Stillingsbrøk 0,5").positionPercent()).isEqualTo(50);
        assertThat(read("Stillingsbrøk 1,0").positionPercent()).isEqualTo(100);
        // "0,5" under a title that means percent is not 50 % and not 1 %: it is left empty
        assertThat(read("Stillingsprosent 0,5").positionPercent()).isNull();
    }

    @Test
    void aSeniorityGivenAsYearsOrADurationIsNeverTurnedIntoADate() {
        assertThat(read("Ansiennitet: 8 år").ansiennitetsdato()).isNull();
        assertThat(read("Ansiennitet: 5 år 3 mnd   pr. 01.10.2026").ansiennitetsdato()).isNull();
        assertThat(read("Lønnsansiennitet: 7 år 1 mnd").ansiennitetsdato()).isNull();
    }

    @Test
    void theDateThePayCountsFromBeatsServiceTimeAndStartDates() {
        assertThat(read("Ansiennitetsdato i kommunen   01.01.2010", "Lønnsansiennitet   01.05.2014").ansiennitetsdato())
                .isEqualTo(YearMonth.of(2014, 5));
        assertThat(read("Ansiennitetsdato i kommunen   01.01.2010").ansiennitetsdato()).isNull();
        assertThat(read("Tiltredelsesdato   01.09.2020", "Ansettelsesdato   01.09.2020").ansiennitetsdato()).isNull();
    }

    @Test
    void aTwoDigitYearIsOnlyAcceptedAfterASeniorityTitle() {
        assertThat(read("Ansiennitetsdato 01.03.18").ansiennitetsdato()).isEqualTo(YearMonth.of(2018, 3));
        assertThat(read("Utbetalingsdato 01.03.18").ansiennitetsdato()).isNull();
    }

    @Test
    void aPlainAnnualSalaryIsOnlyTakenWhenItIsKnownToBeTheFullTimeFigure() {
        // 100 %: own salary and full-time salary are the same
        assertThat(read("Stillingsprosent 100", "Årslønn 560 000,00").annualSalaryFullTime())
                .isEqualByComparingTo(BigDecimal.valueOf(560000));
        // 80 % and a monthly line that equals 560 000 / 12 x 80 %: it is the full-time figure
        assertThat(read("Stillingsprosent 80", "Årslønn 560 000,00", "1010 Månedslønn   37 333,33")
                .annualSalaryFullTime()).isEqualByComparingTo(BigDecimal.valueOf(560000));
        // 80 % and nothing that confirms it: it could be the person's own salary, so it stays empty
        assertThat(read("Stillingsprosent 80", "Årslønn 560 000,00").annualSalaryFullTime()).isNull();
        // the figure is never calculated: a salary that is only implied by the monthly pay is not made up
        assertThat(read("Stillingsprosent 80", "1010 Månedslønn   37 333,33").annualSalaryFullTime()).isNull();
        assertThat(read("Stillingsprosent 80", "Årslønn i stilling 448 000,00").annualSalaryFullTime()).isNull();
    }

    @Test
    void aMainPositionIsPreferredAndTwoUnmarkedPositionsAreNotGuessed() {
        PayslipFields main = read("Tilleggsstilling", "Stillingskode 6572   Stillingsprosent 20,00",
                "Hovedstilling", "Stillingskode 7076   Stillingsprosent 60,00");
        assertThat(main.stillingskode()).isEqualTo("7076");
        assertThat(main.positionPercent()).isEqualTo(60);

        PayslipFields twoMain = read("Hovedstilling", "Stillingskode 6572   Stillingsprosent 20,00",
                "Hovedstilling", "Stillingskode 7076   Stillingsprosent 60,00");
        assertThat(twoMain.stillingskode()).isNull();
        assertThat(twoMain.positionPercent()).isNull();

        PayslipFields table = read("Nordvik kommune",
                "Stillingskode   Stillingsbetegnelse        St-%      Ansiennitet",
                "6572            Assistent                  40,00     01.08.2021",
                "7076            Helsefagarbeider           50,00     01.02.2019");
        assertThat(table.employerName()).isEqualTo("Nordvik kommune");
        assertThat(table.stillingskode()).isNull();
        assertThat(table.jobTitle()).isNull();
        assertThat(table.positionPercent()).isNull();
        assertThat(table.ansiennitetsdato()).isNull();
    }

    @Test
    void payLinesAreNotMistakenForAPosition() {
        PayslipFields fields = read("Testby kommune",
                "Lønnsart   Tekst                 Prosent    Fra dato      Beløp",
                "1010       Månedslønn            100,00     01.10.2026    41 466,67",
                "1033       Sykevikar              25,00     09.10.2026     5 332,67");
        assertThat(fields.stillingskode()).isNull();
        assertThat(fields.jobTitle()).isNull();
        assertThat(fields.positionPercent()).isNull();
        assertThat(fields.ansiennitetsdato()).isNull();
        // a pay-line column called "Betegnelse" is not a job title either
        assertThat(read("Lønnsart   Betegnelse   Antall   Sats   Beløp",
                "1000       Månedslønn   1,00      32 000,00").jobTitle()).isNull();
    }

    @Test
    void theEmployerIsNotAPayrollBureauOrTheTaxMunicipality() {
        assertThat(read("Fjordlys Regnskap og Lønn AS", "Skattekommune Testvik kommune", "Stilling  Assistent")
                .employerName()).isNull();
        assertThat(read("Eksempel Naturbarnehage AS", "Stilling  Barnehagelærer").employerName())
                .isEqualTo("Eksempel Naturbarnehage AS");
        assertThat(read("Universitetet i Testby", "Stilling  Rådgiver").employerName())
                .isEqualTo("Universitetet i Testby");
        // a unit is not the employer when the employer is named
        assertThat(read("Virksomhet   Hjemmetjenesten Sentrum", "Arbeidsgiver   Testby kommune").employerName())
                .isEqualTo("Testby kommune");
    }

    @Test
    void aTitleWithTwoRecognitionMistakesIsStillFound() {
        assertThat(read("Stlllngsprosent   8O,OO %").positionPercent()).isEqualTo(80);
        // the value must not be swallowed by the title: a title followed by a number is not another title
        assertThat(read("Lønnstrinn 34").lonnstrinn()).isEqualTo(34);
    }
}
