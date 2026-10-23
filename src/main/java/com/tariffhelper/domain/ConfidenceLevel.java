package com.tariffhelper.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * How sure the application is about an agreement match or an estimate. A label, never a probability.
 * UNKNOWN is used only for agreement matches.
 */
public enum ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }
}
