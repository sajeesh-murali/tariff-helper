package com.tariffhelper.service;

import com.tariffhelper.domain.AgreementMatch;
import com.tariffhelper.domain.ConfidenceLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AgreementMatcherTest {

    private final AgreementMatcher matcher = new AgreementMatcher();

    @ParameterizedTest
    @ValueSource(strings = {"Oslo kommune", "  OSLO   KOMMUNE  ", "Oslo kommune, Sykehjemsetaten", "Bydel Grünerløkka",
            "Sykehjemsetaten", "Velferdsetaten", "Helseetaten"})
    void osloEmployersAreOsloKommuneWithHighConfidence(String employer) {
        assertMatch(employer, "oslo-kommune", ConfidenceLevel.HIGH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bergen kommune", "Bærum kommune", "Nordre Follo kommune"})
    void otherMunicipalitiesAreKsWithHighConfidence(String employer) {
        assertMatch(employer, "ks-hta", ConfidenceLevel.HIGH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Helse Bergen HF", "Helse Stavanger HF", "Sykehuset Innlandet HF", "Oslo universitetssykehus",
            "Diakonhjemmet Sykehus", "Universitetssykehuset Nord-Norge"})
    void hospitalsAreSpekterWithMediumConfidence(String employer) {
        assertMatch(employer, "spekter", ConfidenceLevel.MEDIUM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Universitetet i Oslo", "NAV Grünerløkka", "Oslo politidistrikt", "Politiet",
            "Forsvaret", "Kriminalomsorgen region øst", "Skatteetaten"})
    void stateEmployersAreStatenWithMediumConfidence(String employer) {
        assertMatch(employer, "staten", ConfidenceLevel.MEDIUM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Navestad Bygg AS", "Politisk forum", "Kommunerevisjonen IKS", "Coop Norge"})
    void otherNamesDoNotMatch(String employer) {
        assertThat(matcher.match(employer)).isEmpty();
    }

    @Test
    void nullDoesNotMatch() {
        assertThat(matcher.match(null)).isEmpty();
    }

    private void assertMatch(String employer, String agreementId, ConfidenceLevel confidence) {
        AgreementMatch match = matcher.match(employer).orElseThrow();
        assertThat(match.agreementId()).as(employer).isEqualTo(agreementId);
        assertThat(match.confidence()).as(employer).isEqualTo(confidence);
        assertThat(match.reason()).isNotBlank();
    }
}
