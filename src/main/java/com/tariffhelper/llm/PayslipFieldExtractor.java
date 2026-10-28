package com.tariffhelper.llm;

import com.tariffhelper.domain.PayslipFields;

/** Reads the form values out of a redacted payslip excerpt. Never throws; anything unusable is left null. */
public interface PayslipFieldExtractor {

    PayslipFields extract(String redactedExcerpt);
}
