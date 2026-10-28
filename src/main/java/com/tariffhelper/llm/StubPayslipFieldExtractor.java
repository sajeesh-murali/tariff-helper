package com.tariffhelper.llm;

import com.tariffhelper.domain.PayslipFields;

/** Used with LLM_PROVIDER=stub: finds nothing, so the person types the payslip values in. No external calls. */
public class StubPayslipFieldExtractor implements PayslipFieldExtractor {

    @Override
    public PayslipFields extract(String redactedExcerpt) {
        return PayslipFields.empty();
    }
}
