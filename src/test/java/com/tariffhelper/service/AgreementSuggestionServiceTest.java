package com.tariffhelper.service;

import com.tariffhelper.api.dto.response.SuggestResponse;
import com.tariffhelper.domain.ConfidenceLevel;
import com.tariffhelper.domain.SuggestionSource;
import com.tariffhelper.llm.AgreementSuggester;
import com.tariffhelper.llm.AgreementSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Which agreement applies: rules in code first, the model only when they cannot decide. The model is mocked. */
@SpringBootTest
class AgreementSuggestionServiceTest {

    @Autowired
    AgreementSuggestionService service;

    @MockitoBean
    AgreementSuggester suggester;

    @BeforeEach
    void unknownByDefault() {
        when(suggester.suggest(any(), any(), anyList())).thenReturn(AgreementSuggestion.unknown());
    }

    @Test
    void osloDistrictIsMatchedByRulesWithTheJobCategory() {
        SuggestResponse response = service.suggest("Bydel Grünerløkka", "sykepleier");

        assertThat(response.source()).isEqualTo(SuggestionSource.RULES);
        assertThat(response.suggestions()).hasSize(1);
        assertThat(response.suggestions().get(0).agreementId()).isEqualTo("oslo-kommune");
        assertThat(response.suggestions().get(0).confidence()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(response.suggestions().get(0).reason()).isNotEmpty();
        assertThat(response.suggestions().get(0).jobCategory()).isEqualTo("nurse");
        verify(suggester, never()).suggest(any(), any(), anyList());
    }

    @Test
    void ksMunicipalityGetsTheKsJobCategory() {
        SuggestResponse response = service.suggest("Bergen kommune", "Barnehageassistent");

        assertThat(response.suggestions().get(0).agreementId()).isEqualTo("ks-hta");
        assertThat(response.suggestions().get(0).jobCategory()).isEqualTo("kindergarten_assistant");
    }

    @Test
    void jobCategoryIsLeftOutWhenTheAgreementDoesNotHaveIt() {
        SuggestResponse oslo = service.suggest("Oslo kommune", "helsefagarbeider");
        assertThat(oslo.suggestions().get(0).agreementId()).isEqualTo("oslo-kommune");
        assertThat(oslo.suggestions().get(0).jobCategory()).isNull();

        SuggestResponse nav = service.suggest("NAV Grünerløkka", "sykepleier");
        assertThat(nav.suggestions().get(0).agreementId()).isEqualTo("staten");
        assertThat(nav.suggestions().get(0).confidence()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(nav.suggestions().get(0).jobCategory()).isNull();
    }

    @Test
    void unmatchedNameAsksTheAiWithOnlyEmployerTitleAndTheAllowlist() {
        when(suggester.suggest(eq("Lovisenberg Diakonale"), isNull(), anyList()))
                .thenReturn(new AgreementSuggestion("spekter", ConfidenceLevel.MEDIUM, "A private hospital."));

        SuggestResponse response = service.suggest("Lovisenberg Diakonale", null);

        assertThat(response.source()).isEqualTo(SuggestionSource.AI);
        assertThat(response.suggestions().get(0).agreementId()).isEqualTo("spekter");
        assertThat(response.suggestions().get(0).confidence()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(response.suggestions().get(0).reason()).isEqualTo("A private hospital.");
        verify(suggester).suggest(eq("Lovisenberg Diakonale"), isNull(),
                eq(List.of("ks-hta", "oslo-kommune", "spekter", "staten")));
    }

    @Test
    void noMatchAndUnknownFromTheAiGivesEmptySuggestions() {
        SuggestResponse response = service.suggest("Coop Norge", "assistent");

        assertThat(response.source()).isEqualTo(SuggestionSource.NONE);
        assertThat(response.suggestions()).isEmpty();
    }
}
