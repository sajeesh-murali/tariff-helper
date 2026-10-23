package com.tariffhelper.domain;

/**
 * The seniority rules that differ between agreements. Missing values in the YAML file default to
 * the KS behaviour.
 */
public record Rules(int careWorkMaxYears, int homeWorkMaxYears,
                    PrivateServiceRule privateService, HomeWorkRule homeWork) {

    public Rules {
        privateService = privateService == null ? PrivateServiceRule.BY_EDUCATION_REQUIREMENT : privateService;
        homeWork = homeWork == null ? HomeWorkRule.IF_NO_EDUCATION_REQUIREMENT : homeWork;
    }

    /** KS-style rules with the given limits. */
    public static Rules of(int careWorkMaxYears, int homeWorkMaxYears) {
        return new Rules(careWorkMaxYears, homeWorkMaxYears, null, null);
    }
}
