package com.tariffhelper.api.dto.response;

import com.tariffhelper.domain.Agreement;

import java.util.List;

/** What the form needs to know about an agreement. Internal rules and pay tables are not exposed. */
public record AgreementSummary(
        String id,
        String name,
        String sector,
        String status,
        String unavailableReason,
        List<JobCategoryOption> jobCategories) {

    public record JobCategoryOption(String id, String label) {
    }

    public static AgreementSummary from(Agreement agreement) {
        List<JobCategoryOption> options = agreement.jobCategories().stream()
                .map(c -> new JobCategoryOption(c.id(), c.label()))
                .toList();
        return new AgreementSummary(agreement.id(), agreement.name(), agreement.sector(),
                agreement.status(), agreement.unavailableReason(), options);
    }
}
