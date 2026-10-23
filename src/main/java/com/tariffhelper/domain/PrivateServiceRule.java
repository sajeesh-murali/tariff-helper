package com.tariffhelper.domain;

/** How private-sector service is credited. */
public enum PrivateServiceRule {
    /** KS: counts in full for positions without an education requirement, otherwise only if related. */
    BY_EDUCATION_REQUIREMENT,
    /** Oslo kommune: all private service with fixed agreed hours counts in full. */
    ALWAYS_FULL
}
