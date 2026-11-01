package com.tariffhelper.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OcrTextCleanerTest {

    @Test
    void lettersInsideDatesAndAmountsBecomeDigits() {
        assertThat(OcrTextCleaner.clean("fra 01.08.2O15 til O1/O8/2O24")).isEqualTo("fra 01.08.2015 til 01/08/2024");
        assertThat(OcrTextCleaner.clean("612 3OO,OO")).isEqualTo("612 300,00");
        assertThat(OcrTextCleaner.clean("trinn l2 og 4l")).isEqualTo("trinn 12 og 41");
    }

    @Test
    void ordinaryWordsAndShortTokensAreLeftAlone() {
        String text = "Oslo kommune, Sykepleier, ll, Ol, Kolloquium og lol";

        assertThat(OcrTextCleaner.clean(text)).isEqualTo(text);
    }

    @Test
    void textWithoutNumbersIsUnchanged() {
        assertThat(OcrTextCleaner.clean("Arbeidsgiver: Bergen kommune\nStilling: Barnehagelærer"))
                .isEqualTo("Arbeidsgiver: Bergen kommune\nStilling: Barnehagelærer");
    }
}
