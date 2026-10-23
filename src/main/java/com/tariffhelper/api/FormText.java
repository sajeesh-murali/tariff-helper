package com.tariffhelper.api;

/**
 * Character whitelist for free text that may be placed in an LLM prompt (employer name, job title).
 * Letters, digits, space and . , & ( ) / ' - only, so the text cannot carry tags or markup.
 */
public final class FormText {

    public static final String REGEX = "^[\\p{L}\\p{N} .,&()/'-]+$";
    public static final String MESSAGE = "may contain only letters, digits, spaces and . , & ( ) / ' -";

    private FormText() {
    }
}
