package com.tariffhelper.llm;

import java.util.List;

/** Used when no real LLM is configured. Makes no external calls and always answers "unknown". */
public class StubAgreementSuggester implements AgreementSuggester {

    @Override
    public AgreementSuggestion suggest(String employerName, String jobTitle, List<String> allowedAgreementIds) {
        return AgreementSuggestion.unknown();
    }
}
