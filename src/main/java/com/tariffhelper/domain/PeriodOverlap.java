package com.tariffhelper.domain;

/** Answer to "did any of these periods happen at the same time?". Decides how summary totals are combined. */
public enum PeriodOverlap {
    /** No overlap: the totals are added. */
    NO,
    /** Some overlap, size unknown: the result is a range (largest single period up to the sum). */
    YES,
    /** The person does not know. Treated like YES. */
    UNSURE
}
