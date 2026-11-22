package com.tariffhelper.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One collective agreement, loaded from {@code agreements/*.yaml}.
 * Placeholder agreements (status "placeholder") have no rules or pay tables, only an unavailableReason.
 *
 * @param effectiveFrom     date the pay tables are valid from (yyyy-MM-dd)
 * @param salaryLadders     minimum-pay ladders keyed by salary group
 * @param rulesSummary      plain-English summary of the rules, kept as a reference; no longer sent to the LLM
 * @param unavailableReason why no estimate can be given (placeholder agreements only)
 * @param lonnstrinnTable   annual salary at 100% position for each lønnstrinn (Oslo kommune's general pay table), or
 *                          empty. Used only to fill in a full-time salary the payslip does not print.
 */
public record Agreement(
        String id,
        String name,
        String sector,
        String status,
        String effectiveFrom,
        List<JobCategory> jobCategories,
        Rules rules,
        Map<String, List<SalaryStep>> salaryLadders,
        String rulesSummary,
        List<String> caveats,
        String unavailableReason,
        Map<Integer, Integer> lonnstrinnTable) {

    /** An agreement without a lønnstrinn table. */
    public Agreement(String id, String name, String sector, String status, String effectiveFrom,
                     List<JobCategory> jobCategories, Rules rules, Map<String, List<SalaryStep>> salaryLadders,
                     String rulesSummary, List<String> caveats, String unavailableReason) {
        this(id, name, sector, status, effectiveFrom, jobCategories, rules, salaryLadders, rulesSummary, caveats,
                unavailableReason, null);
    }

    public Agreement {
        jobCategories = jobCategories == null ? List.of() : List.copyOf(jobCategories);
        salaryLadders = salaryLadders == null ? Map.of() : Map.copyOf(salaryLadders);
        caveats = caveats == null ? List.of() : List.copyOf(caveats);
        lonnstrinnTable = lonnstrinnTable == null ? Map.of() : Map.copyOf(lonnstrinnTable);
    }

    /** The annual salary at 100% position for this lønnstrinn, if the agreement has a pay table that lists it. */
    public Optional<Integer> annualSalaryForStep(int lonnstrinn) {
        return Optional.ofNullable(lonnstrinnTable.get(lonnstrinn));
    }

    /** True when the agreement is modelled well enough to give an estimate. */
    public boolean hasRules() {
        return rules != null && !salaryLadders.isEmpty();
    }

    public Optional<JobCategory> findJobCategory(String jobCategoryId) {
        return jobCategories.stream().filter(c -> c.id().equals(jobCategoryId)).findFirst();
    }
}
