package com.tariffhelper.api.dto.response;

import com.tariffhelper.domain.ConfidenceLevel;
import com.tariffhelper.domain.PerExperience;
import com.tariffhelper.domain.SalaryRange;
import com.tariffhelper.domain.SuggestionSource;

import java.math.BigDecimal;
import java.util.List;

/**
 * All salary figures are annual, for a full-time (100%) position.
 * The ranges run from "only what counts" (min) to "if the employer accepts everything uncertain" (max).
 *
 * @param agreementSuggestion present only when the agreement was not given in the request
 * @param confidence          calculated in code, never by the LLM; a label with reasons, not a probability
 * @param creditedVsCounted   present only when payslip values allowed a comparison, otherwise null
 */
public record EstimateResponse(
        String agreementApplied,
        String rulesValidFrom,
        String jobCategory,
        String salaryGroup,
        BigDecimal currentFullTimeSalary,
        BigDecimal countedYears,
        BigDecimal countedYearsIfAllAccepted,
        SalaryRange estimatedMinimumSalary,
        SalaryRange differenceFromCurrent,
        List<PerExperience> perExperience,
        String explanation,
        List<String> caveats,
        String disclaimer,
        AgreementSuggestionInfo agreementSuggestion,
        Confidence confidence,
        CreditedVsCounted creditedVsCounted) {

    /** @param assumed true: the estimate uses this agreement although the person did not choose it */
    public record AgreementSuggestionInfo(String agreementId, ConfidenceLevel confidence,
                                          SuggestionSource source, boolean assumed) {
    }

    public record Confidence(ConfidenceLevel level, List<String> reasons) {
    }

    /** @param basis "lonnstrinn" or "ansiennitetsdato": which payslip value the credited years come from */
    public record CreditedVsCounted(BigDecimal creditedYears, BigDecimal countedYears, String basis) {
    }
}
