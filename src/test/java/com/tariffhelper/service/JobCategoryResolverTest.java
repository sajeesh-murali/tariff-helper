package com.tariffhelper.service;

import com.tariffhelper.agreement.AgreementRepository;
import com.tariffhelper.domain.Agreement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobCategoryResolverTest {

    private static final AgreementRepository REPOSITORY = new AgreementRepository();

    private final JobCategoryResolver resolver = new JobCategoryResolver();

    private static Agreement ks() {
        return REPOSITORY.findById("ks-hta").orElseThrow();
    }

    private static Agreement oslo() {
        return REPOSITORY.findById("oslo-kommune").orElseThrow();
    }

    @ParameterizedTest
    @CsvSource({
            "sykepleier, nurse",
            "Nurse, nurse",
            "Spesialsykepleier, specialist_nurse",
            "barnehagelærer, kindergarten_teacher",
            "Helsefagarbeider, health_care_worker",
            "Barnehageassistent, kindergarten_assistant",
            "assistent, care_assistant",
            "Pleieassistent, care_assistant"})
    void ksTitlesMapToCategories(String title, String expected) {
        assertThat(resolver.fromJobTitle(ks(), title)).contains(expected);
    }

    @Test
    void onlyCategoriesThatExistInTheAgreementAreReturned() {
        assertThat(resolver.fromJobTitle(oslo(), "sykepleier")).contains("nurse");
        assertThat(resolver.fromJobTitle(oslo(), "helsefagarbeider")).isEmpty();
        assertThat(resolver.fromJobTitle(oslo(), "barnehageassistent")).isEmpty();
    }

    @Test
    void unknownOrMissingTitlesGiveNothing() {
        assertThat(resolver.fromJobTitle(ks(), "lege")).isEmpty();
        assertThat(resolver.fromJobTitle(ks(), null)).isEmpty();
        assertThat(resolver.fromJobTitle(ks(), " ")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"58, nurse", "59, specialist_nurse", "60, specialist_nurse", "61, specialist_nurse",
            "94, clinical_nurse"})
    void osloStillingskoderMapToCategories(String code, String expected) {
        assertThat(resolver.fromStillingskode(oslo(), code)).contains(expected);
    }

    @Test
    void stillingskodeIsOnlyKnownForOslo() {
        assertThat(resolver.fromStillingskode(oslo(), "7174")).isEmpty();
        assertThat(resolver.fromStillingskode(ks(), "58")).isEmpty();
        assertThat(resolver.fromStillingskode(oslo(), null)).isEmpty();
    }
}
