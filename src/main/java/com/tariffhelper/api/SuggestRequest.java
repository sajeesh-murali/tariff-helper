package com.tariffhelper.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Which agreement applies? Only the employer name (required) and job title (optional). */
public record SuggestRequest(
        @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = FormText.REGEX, message = FormText.MESSAGE)
        String employerName,
        @Size(max = 60) @Pattern(regexp = FormText.REGEX, message = FormText.MESSAGE)
        String jobTitle) {
}
