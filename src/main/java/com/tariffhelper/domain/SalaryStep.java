package com.tariffhelper.domain;

import java.math.BigDecimal;

/** One step of a minimum-pay ladder: from this many completed years of seniority, this annual pay. */
public record SalaryStep(int fromYears, BigDecimal annualSalary) {
}
