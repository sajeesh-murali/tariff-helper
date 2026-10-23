package com.tariffhelper.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Where an agreement suggestion came from: the rules in code, the AI (Call 1), or nowhere (no match). */
public enum SuggestionSource {
    RULES,
    AI,
    NONE;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }
}
