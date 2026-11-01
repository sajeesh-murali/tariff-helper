package com.tariffhelper.service;

import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PayslipRedactorTest {

    private final PayslipRedactor redactor = new PayslipRedactor();

    @Test
    void keepsOnlyTheLabelledLinesAndTheRowsBelowThem() {
        String text = String.join("\n",
                "Kari Testesen",
                "Fodselsnummer: 01019012345",
                "Stillingskode Stilling Lonnstrinn Ansiennitet fra",
                "7936 Sykepleier 45 01.08.2015",
                "Skattetrekk 12 345,00",
                "Utbetalt 30 000,00");

        String excerpt = redactor.excerpt(text);

        assertThat(excerpt).contains("7936 Sykepleier 45 01.08.2015");
        assertThat(excerpt).doesNotContain("Kari").doesNotContain("01019012345").doesNotContain("Utbetalt");
    }

    @Test
    void aLabelThatTextRecognitionGotSlightlyWrongStillMarksItsLine() {
        String text = String.join("\n",
                "Arbeidsg1ver: Oslo kommune",
                "Ansiennltet fra 01.08.2015",
                "Utbetalt 30 000,00");

        assertThat(redactor.excerpt(text))
                .contains("Arbeidsg1ver: Oslo kommune")
                .contains("Ansiennltet fra 01.08.2015");
    }

    @Test
    void anUnlabelledLetterheadEmployerIsKeptButNotTheAddressAndNameAroundIt() {
        String text = String.join("\n",
                "Oslo kommune",
                "Bydel Vestre Aker",
                "Sørkedalsveien 150 A",
                "0754 OSLO",
                "Ola Nordmann",
                "Stilling Stillingskode - Tittel St-% Ansiennitet Ltr",
                "S160076 0058000 - Sykepleier 0 01.12.2024 31");

        String excerpt = redactor.excerpt(text);

        assertThat(excerpt).contains("Oslo kommune").contains("S160076 0058000 - Sykepleier 0 01.12.2024 31");
        assertThat(excerpt).doesNotContain("Sørkedalsveien").doesNotContain("0754").doesNotContain("Ola Nordmann");
    }

    @Test
    void aLongOrNumberedLineWithKommuneIsNotTakenForAnEmployerName() {
        String text = "Kommunen betaler ut lønn den 28. hver måned etter avtale mellom partene i kommune og stat\n"
                + "Bydel 3 kommune 4";

        assertThat(redactor.excerpt(text)).isEmpty();
    }

    @Test
    void wordsThatOnlyLookALittleLikeALabelAreNotKept() {
        String text = "Skattetrekk 12 345,00\nTrekkgrunnlag 500 000,00\nUtbetalt 30 000,00";

        assertThat(redactor.excerpt(text)).isEmpty();
    }

    @Test
    void dropsLinesCarryingAPersonalLabelEvenWhenTheyMatchAKeyword() {
        String text = "Stillingskode 7936\nKontonummer 4202.15.12345\nAnsiennitet 2015\nNavn Kari Testesen\n";

        assertThat(redactor.excerpt(text))
                .contains("Stillingskode 7936")
                .doesNotContain("4202").doesNotContain("Kari");
    }

    @Test
    void masksIdAccountPhoneIbanAndEmailPatterns() {
        String text = String.join("\n",
                "Stilling 7936 id 010190 12345 acc 4202.15.12345 tlf +47 12345678 8 siffer 98765432",
                "Stilling NO93 8601 1117 947 post a.b@c.no");

        String excerpt = redactor.excerpt(text);

        assertThat(excerpt)
                .doesNotContain("010190").doesNotContain("4202").doesNotContain("12345678")
                .doesNotContain("98765432").doesNotContain("8601").doesNotContain("a.b@c.no")
                .contains("7936")
                .contains(PayslipRedactor.MASK);
        // Every pattern was exercised on a kept line: id, account, phone, 8 digits, IBAN and email.
        assertThat(excerpt.split("\\[removed]", -1).length - 1).isEqualTo(6);
    }

    @Test
    void keepsTheEmployerSalaryAndPercentLinesEvenWhenTheEmployerLineSaysNavn() {
        String text = String.join("\n",
                "Arbeidsgivernavn: Oslo kommune",
                "Navn: Kari Testesen",
                "Stillingsprosent: 80 %",
                "ÅRSLØNN I 100 % STILLING FØR SKATT 612 300,00");

        String excerpt = redactor.excerpt(text);

        assertThat(excerpt)
                .contains("Arbeidsgivernavn: Oslo kommune")
                .contains("Stillingsprosent: 80 %")
                .contains("ÅRSLØNN I 100 % STILLING FØR SKATT 612 300,00")
                .doesNotContain("Kari");
    }

    @Test
    void doesNotMaskThePayStepOrTheDate() {
        assertThat(redactor.excerpt("Lonnstrinn 45 Ansiennitet fra 01.08.2015"))
                .isEqualTo("Lonnstrinn 45 Ansiennitet fra 01.08.2015");
    }

    @Test
    void returnsNothingWhenNoLineHasAKeyword() {
        assertThat(redactor.excerpt("Kari Testesen\nSkattetrekk 12 345,00\nUtbetalt 30 000,00")).isEmpty();
    }

    @Test
    void capsTheNumberOfLinesAndTheLengthOfEachLine() {
        String many = IntStream.range(0, 100).mapToObj(i -> "Stilling " + i).collect(Collectors.joining("\n"));
        assertThat(redactor.excerpt(many).split("\n")).hasSize(PayslipRedactor.MAX_LINES);

        String longLine = "Stilling " + "x".repeat(1000);
        assertThat(redactor.excerpt(longLine)).hasSizeLessThanOrEqualTo(PayslipRedactor.MAX_LINE_CHARS);
    }
}
