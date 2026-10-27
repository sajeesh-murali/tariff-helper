package com.tariffhelper.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

/**
 * The values read from a payslip, named like the matching fields of the estimate form. Any of them may be null when
 * it was not found or did not pass the checks. They are only suggestions for the person to confirm; the estimate
 * re-validates whatever is submitted.
 *
 * @param annualSalaryFullTime the "annual salary in a 100 % position before tax" exactly as printed, whole NOK
 */
public record PayslipFields(String stillingskode, Integer lonnstrinn, YearMonth ansiennitetsdato,
                            String employerName, String jobTitle, Integer positionPercent,
                            BigDecimal annualSalaryFullTime) {

    public static PayslipFields empty() {
        return new PayslipFields(null, null, null, null, null, null, null);
    }

    /** What the person earns per year at their own position percentage; null unless both values were read. */
    public BigDecimal currentAnnualSalary() {
        if (annualSalaryFullTime == null || positionPercent == null) {
            return null;
        }
        return annualSalaryFullTime.multiply(BigDecimal.valueOf(positionPercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    public int foundCount() {
        int count = 0;
        for (Object value : new Object[]{stillingskode, lonnstrinn, ansiennitetsdato, employerName, jobTitle,
                positionPercent, annualSalaryFullTime}) {
            if (value != null) {
                count++;
            }
        }
        return count;
    }
}
