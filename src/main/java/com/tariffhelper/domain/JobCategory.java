package com.tariffhelper.domain;

/**
 * A job the user can pick on the form.
 *
 * @param educationRequired whether the position has a specific education requirement.
 *                          This decides how private service and home work are credited.
 * @param salaryGroup       key into the agreement's salaryLadders
 * @param note              optional assumption to show the user (for example an unconfirmed mapping)
 */
public record JobCategory(
        String id,
        String label,
        Boolean educationRequired,
        String salaryGroup,
        String note) {

    public boolean requiresEducation() {
        return Boolean.TRUE.equals(educationRequired);
    }
}
