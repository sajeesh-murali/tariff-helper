package com.tariffhelper.llm;

import java.util.List;

/**
 * Call 1: suggests which agreement an employer uses, when the rules in code cannot decide.
 * Only the employer name and job title are sent. Never throws: any failure is "unknown".
 */
public interface AgreementSuggester {

    AgreementSuggestion suggest(String employerName, String jobTitle, List<String> allowedAgreementIds);
}
