package com.tariffhelper.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Used when no real LLM is configured. Makes no external calls and always answers "unknown". */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "stub", matchIfMissing = true)
public class StubAgreementSuggester implements AgreementSuggester {

    @Override
    public AgreementSuggestion suggest(String employerName, String jobTitle, List<String> allowedAgreementIds) {
        return AgreementSuggestion.unknown();
    }
}
