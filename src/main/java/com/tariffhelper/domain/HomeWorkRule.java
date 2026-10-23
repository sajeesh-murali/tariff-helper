package com.tariffhelper.domain;

/** How work in the home is credited. */
public enum HomeWorkRule {
    /** KS: counts (up to homeWorkMaxYears) for positions without an education requirement, otherwise not. */
    IF_NO_EDUCATION_REQUIREMENT,
    /**
     * Oslo kommune: work in the home is not mentioned in the rules. It may be accepted as care work, so it is
     * shown as uncertain, up to homeWorkMaxYears and sharing that limit with care work already counted.
     */
    POSSIBLE_ONLY
}
