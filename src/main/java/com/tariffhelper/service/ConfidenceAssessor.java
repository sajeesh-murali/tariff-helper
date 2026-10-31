package com.tariffhelper.service;

import com.tariffhelper.api.dto.response.EstimateResponse.AgreementSuggestionInfo;
import com.tariffhelper.api.dto.response.EstimateResponse.Confidence;
import com.tariffhelper.domain.ConfidenceLevel;
import com.tariffhelper.domain.CreditedSeniority;
import com.tariffhelper.domain.SeniorityResult;
import com.tariffhelper.domain.SuggestionSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The confidence level of an estimate, calculated in code (never taken from the LLM). It is a label with
 * reasons in Norwegian, not a probability.
 *
 * - low: the agreement came from the AI below high confidence, or the payslip step could not be reconciled.
 * - medium: uncertain months, a medium agreement match (or any AI match), an inferred job group, payslip
 *   values that disagree with the counted years, or work history given as totals without dates.
 * - high: everything else (agreement given or matched by rules with high confidence, nothing uncertain).
 */
@Component
public class ConfidenceAssessor {

    static final String FEWER_YEARS = "Lønnsslippen din tyder på færre år enn reglene teller";
    static final String MORE_YEARS = "Arbeidsgiveren din godskriver allerede mer enn vi klarte å telle";

    private static final String[] NUMBER_WORDS =
            {"Ingen", "Én", "To", "Tre", "Fire", "Fem", "Seks", "Sju", "Åtte", "Ni", "Ti"};

    /** @param suggestion null when the person chose the agreement */
    public Confidence assess(AgreementSuggestionInfo suggestion, SeniorityResult seniority,
                             JobCategoryBasis jobBasis, CreditedSeniority credited) {
        return assess(suggestion, seniority, jobBasis, credited, ScreeningSeniorityCalculator.Info.NONE);
    }

    /** @param screening what the questionnaire answers (totals without dates) contributed */
    public Confidence assess(AgreementSuggestionInfo suggestion, SeniorityResult seniority,
                             JobCategoryBasis jobBasis, CreditedSeniority credited,
                             ScreeningSeniorityCalculator.Info screening) {
        List<String> reasons = new ArrayList<>();
        boolean low = false;
        boolean medium = false;

        if (suggestion == null) {
            reasons.add("Du valgte avtalen selv");
        } else if (suggestion.source() == SuggestionSource.AI) {
            reasons.add("Avtalen ble foreslått av KI ut fra arbeidsgivernavnet (" + levelWord(suggestion.confidence())
                    + " sikkerhet). Spør arbeidsgiveren din hvilken avtale som gjelder");
            low = suggestion.confidence() != ConfidenceLevel.HIGH;
            medium = true;
        } else if (suggestion.confidence() == ConfidenceLevel.HIGH) {
            reasons.add("Avtalen ble funnet ut fra arbeidsgivernavnet med faste regler");
        } else {
            reasons.add("Avtalen ble gjettet ut fra arbeidsgivernavnet og kan være feil. "
                    + "Spør arbeidsgiveren din hvilken avtale som gjelder");
            medium = true;
        }

        // The level follows the range (min != max); the count only words the reason.
        long uncertainPeriods = Math.max(1, seniority.perExperience().stream()
                .filter(p -> p.possibleExtraMonths() > 0).count());
        if (seniority.possibleExtraMonths() > 0) {
            reasons.add(uncertainPeriods == 1 ? "Én periode avhenger av arbeidsgiverens vurdering"
                    : count(uncertainPeriods) + " perioder avhenger av arbeidsgiverens vurdering");
            medium = true;
        } else {
            reasons.add("Ingen perioder avhenger av arbeidsgiverens vurdering");
        }

        if (screening.used()) {
            reasons.add("Deler av arbeidshistorikken din ble oppgitt som samlet tid uten datoer, så dette er et "
                    + "foreløpig anslag");
            medium = true;
        }
        if (screening.approximate()) {
            reasons.add("Du markerte noen svar som omtrentlige");
            medium = true;
        }
        if (screening.overlapUncertain()) {
            reasons.add("Noen perioder kan ha skjedd samtidig, så summen vises som et intervall");
            medium = true;
        }

        if (jobBasis == JobCategoryBasis.STILLINGSKODE) {
            reasons.add("Stillingsgruppen ble hentet fra stillingskoden din");
            medium = true;
        } else if (jobBasis == JobCategoryBasis.JOB_TITLE) {
            reasons.add("Stillingsgruppen ble hentet fra stillingstittelen din. Sjekk at den passer med stillingen din");
            medium = true;
        }

        if (credited.stepNotOnLadder()) {
            reasons.add("Lønnstrinnet ditt finnes ikke i lønnsstigen for denne stillingen, så det kunne ikke "
                    + "sammenlignes med ansienniteten som teller");
            low = true;
        }
        if (credited.stepNumbersMissing()) {
            reasons.add("Lønnstrinn kan ikke kontrolleres for denne avtalen ennå, så det ble ikke brukt");
        }
        if (credited.compared()) {
            BigDecimal counted = seniority.countedYears();
            if (credited.creditedYears().compareTo(counted.subtract(BigDecimal.ONE)) < 0) {
                reasons.add(FEWER_YEARS);
                medium = true;
            } else if (credited.creditedYears().compareTo(counted) > 0) {
                reasons.add(MORE_YEARS);
                medium = true;
            }
        }

        ConfidenceLevel level = low ? ConfidenceLevel.LOW : medium ? ConfidenceLevel.MEDIUM : ConfidenceLevel.HIGH;
        return new Confidence(level, List.copyOf(reasons));
    }

    private static String levelWord(ConfidenceLevel level) {
        return switch (level) {
            case HIGH -> "høy";
            case MEDIUM -> "middels";
            case LOW -> "lav";
            case UNKNOWN -> "ukjent";
        };
    }

    private static String count(long n) {
        return n < NUMBER_WORDS.length ? NUMBER_WORDS[(int) n] : Long.toString(n);
    }
}
