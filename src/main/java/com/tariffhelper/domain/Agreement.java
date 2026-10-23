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
 * @param rulesSummary      plain-English summary of the rules, given to the LLM for the explanation
 * @param unavailableReason why no estimate can be given (placeholder agreements only)
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
        String unavailableReason) {

    public Agreement {
        jobCategories = jobCategories == null ? List.of() : List.copyOf(jobCategories);
        salaryLadders = salaryLadders == null ? Map.of() : Map.copyOf(salaryLadders);
        caveats = caveats == null ? List.of() : List.copyOf(caveats);
    }

    /** True when the agreement is modelled well enough to give an estimate. */
    public boolean hasRules() {
        return rules != null && !salaryLadders.isEmpty();
    }

    public Optional<JobCategory> findJobCategory(String jobCategoryId) {
        return jobCategories.stream().filter(c -> c.id().equals(jobCategoryId)).findFirst();
    }
}
