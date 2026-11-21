package com.tariffhelper.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlternativPairTest {

    private static final String HEADER = "Stilling Stillingskode - Tittel St-% Ansiennitet L-ramme - Alt Ltr Orgenhet\n";

    @Test
    void theAlternativAloneIsRefused() {
        String lost = HEADER + "S160076 0058000 - Sykepleier 01.12.2024 026 - 8 Hjemmetjeneste";
        assertThat(AlternativPair.isOnlyTheAlternativ(8, lost)).isTrue();
    }

    @Test
    void theRealStepNextToTheAlternativIsKept() {
        String whole = HEADER + "S160076 0058000 - Sykepleier 0 01.12.2024 026 - 8 31 Hjemmetjeneste";
        assertThat(AlternativPair.isOnlyTheAlternativ(31, whole)).isFalse();
        // a step that equals the alternativ is printed twice
        String same = HEADER + "S160076 0058000 - Sykepleier 0 01.12.2024 026 - 8 8 Hjemmetjeneste";
        assertThat(AlternativPair.isOnlyTheAlternativ(8, same)).isFalse();
    }

    @Test
    void theTitleMayHaveLostItsAltWord() {
        String text = "St-% Ansiennitet L-ramme Ltr\nS160076 0058000 - Sykepleier 01.12.2024 026 - 8 Hjemmetjeneste";
        assertThat(AlternativPair.isOnlyTheAlternativ(8, text)).isTrue();
    }

    @Test
    void textWithoutThePairTitleIsNeverRefused() {
        assertThat(AlternativPair.isOnlyTheAlternativ(8, "Lonnstrinn 8 Stillingskode 7936 periode 2026-8")).isFalse();
        assertThat(AlternativPair.isOnlyTheAlternativ(8, null)).isFalse();
    }
}
