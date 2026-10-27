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
 * plain-English reasons, not a probability.
 *
 * - low: the agreement came from the AI below high confidence, or the payslip step could not be reconciled.
 * - medium: uncertain months, a medium agreement match (or any AI match), an inferred job group, payslip
 *   values that disagree with the counted years, or work history given as totals without dates.
 * - high: everything else (agreement given or matched by rules with high confidence, nothing uncertain).
 */
@Component
public class ConfidenceAssessor {

    static final String FEWER_YEARS = "Your payslip suggests fewer years than the rules count";
    static final String MORE_YEARS = "Your employer already credits more than we could count";

    private static final String[] NUMBER_WORDS =
            {"No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten"};

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
            reasons.add("You chose the agreement");
        } else if (suggestion.source() == SuggestionSource.AI) {
            reasons.add("The agreement was suggested by AI from the employer name (" + suggestion.confidence().json()
                    + " confidence). Check with your employer which agreement applies");
            low = suggestion.confidence() != ConfidenceLevel.HIGH;
            medium = true;
        } else if (suggestion.confidence() == ConfidenceLevel.HIGH) {
            reasons.add("The agreement was found from the employer name by fixed rules");
        } else {
            reasons.add("The agreement was guessed from the employer name and may be wrong. "
                    + "Check with your employer which agreement applies");
            medium = true;
        }

        // The level follows the range (min != max); the count only words the reason.
        long uncertainPeriods = Math.max(1, seniority.perExperience().stream()
                .filter(p -> p.possibleExtraMonths() > 0).count());
        if (seniority.possibleExtraMonths() > 0) {
            reasons.add(uncertainPeriods == 1 ? "One period depends on the employer's decision"
                    : count(uncertainPeriods) + " periods depend on the employer's decision");
            medium = true;
        } else {
            reasons.add("No period depends on the employer's decision");
        }

        if (screening.used()) {
            reasons.add("Part of your work history was entered as total time without dates, so this is a "
                    + "preliminary estimate");
            medium = true;
        }
        if (screening.approximate()) {
            reasons.add("You marked some answers as approximate");
            medium = true;
        }
        if (screening.overlapUncertain()) {
            reasons.add("Some periods may have happened at the same time, so the total is shown as a range");
            medium = true;
        }

        if (jobBasis == JobCategoryBasis.STILLINGSKODE) {
            reasons.add("The job group was taken from your stillingskode");
            medium = true;
        } else if (jobBasis == JobCategoryBasis.JOB_TITLE) {
            reasons.add("The job group was taken from your job title. Check that it matches your position");
            medium = true;
        }

        if (credited.stepNotOnLadder()) {
            reasons.add("Your lønnstrinn is not on the pay ladder for this position, so it could not be compared "
                    + "with the counted years");
            low = true;
        }
        if (credited.stepNumbersMissing()) {
            reasons.add("Lønnstrinn cannot be checked for this agreement yet, so it was not used");
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

    private static String count(long n) {
        return n < NUMBER_WORDS.length ? NUMBER_WORDS[(int) n] : Long.toString(n);
    }
}
