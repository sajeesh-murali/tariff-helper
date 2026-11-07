package com.tariffhelper.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

/**
 * The values read from a payslip, named like the matching fields of the estimate form. Any of them may be null when
 * it was not found or did not pass the checks. They are only suggestions for the person to confirm; the estimate
 * re-validates whatever is submitted.
 *
 * @param annualSalaryFullTime the "annual salary in a 100 % position before tax", whole NOK: as printed, or, when
 *                             only the monthly pay is printed, calculated in code (see PayslipRuleReader)
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

    /** The same values without the annual salary (used when a calculated salary gives way to a printed one). */
    public PayslipFields withoutAnnualSalary() {
        return new PayslipFields(stillingskode, lonnstrinn, ansiennitetsdato, employerName, jobTitle, positionPercent,
                null);
    }

    /** Every value that is missing here is taken from the other reading; a value found here is never replaced. */
    public PayslipFields fillGapsFrom(PayslipFields other) {
        return new PayslipFields(
                stillingskode != null ? stillingskode : other.stillingskode,
                lonnstrinn != null ? lonnstrinn : other.lonnstrinn,
                ansiennitetsdato != null ? ansiennitetsdato : other.ansiennitetsdato,
                employerName != null ? employerName : other.employerName,
                jobTitle != null ? jobTitle : other.jobTitle,
                positionPercent != null ? positionPercent : other.positionPercent,
                annualSalaryFullTime != null ? annualSalaryFullTime : other.annualSalaryFullTime);
    }

    /** Whether the value with this form name (employerName, jobTitle, ...) is present. */
    public boolean has(String field) {
        return switch (field) {
            case "employerName" -> employerName != null;
            case "jobTitle" -> jobTitle != null;
            case "positionPercent" -> positionPercent != null;
            case "annualSalaryFullTime" -> annualSalaryFullTime != null;
            case "stillingskode" -> stillingskode != null;
            case "lonnstrinn" -> lonnstrinn != null;
            case "ansiennitetsdato" -> ansiennitetsdato != null;
            default -> false;
        };
    }

    /** The value with this form name, or null. */
    public Object valueOf(String field) {
        return switch (field) {
            case "employerName" -> employerName;
            case "jobTitle" -> jobTitle;
            case "positionPercent" -> positionPercent;
            case "annualSalaryFullTime" -> annualSalaryFullTime;
            case "stillingskode" -> stillingskode;
            case "lonnstrinn" -> lonnstrinn;
            case "ansiennitetsdato" -> ansiennitetsdato;
            default -> null;
        };
    }

    public static final java.util.List<String> FIELD_NAMES = java.util.List.of("employerName", "jobTitle",
            "positionPercent", "annualSalaryFullTime", "stillingskode", "lonnstrinn", "ansiennitetsdato");

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
