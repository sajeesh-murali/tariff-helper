package com.tariffhelper.domain;

import java.math.BigDecimal;

/**
 * Everything the application has worked out. The LLM is given these numbers to explain,
 * and must not change them.
 *
 * @param currentFullTimeSalary    the user's current pay converted to a full-time (100%) annual figure
 * @param estimatedMinimumSalary   agreement minimum pay (full-time, annual): at the counted seniority,
 *                                 and if all uncertain periods are accepted
 * @param difference               estimatedMinimumSalary minus currentFullTimeSalary (can be negative)
 * @param payslipConclusion        the code's conclusion from payslip values, in words, or null.
 *                                 Never the raw payslip values.
 */
public record EstimateFacts(
        Agreement agreement,
        JobCategory jobCategory,
        SeniorityResult seniority,
        BigDecimal currentFullTimeSalary,
        SalaryRange estimatedMinimumSalary,
        SalaryRange difference,
        String payslipConclusion) {

    public EstimateFacts(Agreement agreement, JobCategory jobCategory, SeniorityResult seniority,
                         BigDecimal currentFullTimeSalary, SalaryRange estimatedMinimumSalary, SalaryRange difference) {
        this(agreement, jobCategory, seniority, currentFullTimeSalary, estimatedMinimumSalary, difference, null);
    }
}
