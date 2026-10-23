package com.tariffhelper.domain;

import java.math.BigDecimal;

/**
 * One step of a minimum-pay ladder: from this many completed years of seniority, this annual pay.
 *
 * @param step the published pay step number (lønnstrinn), if the agreement has one; null otherwise.
 *             Used only to read credited seniority from a payslip, never to calculate pay.
 */
public record SalaryStep(int fromYears, BigDecimal annualSalary, Integer step) {

    public SalaryStep(int fromYears, BigDecimal annualSalary) {
        this(fromYears, annualSalary, null);
    }
}
