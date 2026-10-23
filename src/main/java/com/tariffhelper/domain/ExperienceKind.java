package com.tariffhelper.domain;

/** What kind of period a work-history entry is. Agreements treat each kind differently. */
public enum ExperienceKind {
    /** Employed in the public sector (state, county, municipality, public bodies). */
    PUBLIC_SERVICE,
    /** Employed in the private sector. */
    PRIVATE_SERVICE,
    /** Ran their own business. Assessed by the employer, usually from tax records. */
    SELF_EMPLOYED,
    /** Unpaid care of children, or of elderly or sick people. */
    CARE_WORK,
    /** Unpaid work in the home. */
    HOME_WORK,
    /** Compulsory military service. */
    MILITARY_SERVICE
}
