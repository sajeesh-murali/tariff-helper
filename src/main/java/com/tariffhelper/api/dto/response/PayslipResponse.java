package com.tariffhelper.api.dto.response;

import com.tariffhelper.domain.ConfidenceLevel;
import com.tariffhelper.domain.PayslipFields;
import com.tariffhelper.domain.SuggestionSource;
import com.tariffhelper.service.PayslipService;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Values read from the payslip and the agreement suggested from the employer, to be shown to the person for
 * checking. Any value may be null. The names match the estimate form. currentAnnualSalary is calculated by the
 * application from the printed full-time salary and the position percentage; it is what the estimate form asks for.
 *
 * @param agreement the suggested agreement, or null when none could be chosen (the person picks one from the list)
 */
public record PayslipResponse(String employerName, String jobTitle, Integer positionPercent,
                              BigDecimal annualSalaryFullTime, BigDecimal currentAnnualSalary,
                              String stillingskode, Integer lonnstrinn, YearMonth ansiennitetsdato,
                              SuggestedAgreement agreement, String note) {

    static final String NOTE = "These values were read automatically. Check them against your payslip before using them.";

    /**
     * @param status            "available" or "placeholder"; a placeholder agreement cannot be estimated yet
     * @param unavailableReason why, when the status is "placeholder"
     * @param source            "rules" (code) or "ai" (validated against the list of agreements)
     * @param jobCategory       set only when the job title matches one of this agreement's job categories
     */
    public record SuggestedAgreement(String agreementId, String name, String status, String unavailableReason,
                                     ConfidenceLevel confidence, String reason, SuggestionSource source,
                                     String jobCategory) {
    }

    public static PayslipResponse from(PayslipService.Result result) {
        PayslipFields fields = result.fields();
        return new PayslipResponse(fields.employerName(), fields.jobTitle(), fields.positionPercent(),
                fields.annualSalaryFullTime(), fields.currentAnnualSalary(),
                fields.stillingskode(), fields.lonnstrinn(), fields.ansiennitetsdato(),
                suggested(result), NOTE);
    }

    private static SuggestedAgreement suggested(PayslipService.Result result) {
        if (result.agreement() == null || result.suggestion().suggestions().isEmpty()) {
            return null;
        }
        SuggestResponse.Suggestion top = result.suggestion().suggestions().get(0);
        return new SuggestedAgreement(top.agreementId(), result.agreement().name(), result.agreement().status(),
                result.agreement().unavailableReason(), top.confidence(), top.reason(),
                result.suggestion().source(), top.jobCategory());
    }
}
