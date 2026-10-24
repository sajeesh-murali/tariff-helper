package com.tariffhelper.service;

import com.tariffhelper.agreement.AgreementRepository;
import com.tariffhelper.api.dto.response.SuggestResponse;
import com.tariffhelper.api.dto.response.SuggestResponse.Suggestion;
import com.tariffhelper.domain.Agreement;
import com.tariffhelper.domain.AgreementMatch;
import com.tariffhelper.domain.SuggestionSource;
import com.tariffhelper.llm.AgreementSuggester;
import com.tariffhelper.llm.AgreementSuggestion;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Which agreement applies: the rules in code first, then Claude (Call 1) only if the rules cannot decide.
 * Privacy: the employer name and job title are never logged.
 */
@Service
public class AgreementSuggestionService {

    private final AgreementRepository agreements;
    private final AgreementMatcher matcher;
    private final AgreementSuggester suggester;
    private final JobCategoryResolver jobCategories;

    public AgreementSuggestionService(AgreementRepository agreements, AgreementMatcher matcher,
                                      AgreementSuggester suggester, JobCategoryResolver jobCategories) {
        this.agreements = agreements;
        this.matcher = matcher;
        this.suggester = suggester;
        this.jobCategories = jobCategories;
    }

    public SuggestResponse suggest(String employerName, String jobTitle) {
        Optional<AgreementMatch> byRules = matcher.match(employerName);
        if (byRules.isPresent()) {
            return response(byRules.get(), jobTitle, SuggestionSource.RULES);
        }
        List<String> allowed = agreements.findAll().stream().map(Agreement::id).toList();
        AgreementSuggestion byAi = suggester.suggest(employerName, jobTitle, allowed);
        if (byAi.isUnknown()) {
            return SuggestResponse.none();
        }
        return response(new AgreementMatch(byAi.agreementId(), byAi.confidence(), byAi.reason()),
                jobTitle, SuggestionSource.AI);
    }

    private SuggestResponse response(AgreementMatch match, String jobTitle, SuggestionSource source) {
        // The id was checked against the repository (AI) or is fixed in code (rules), so it exists.
        Agreement agreement = agreements.findById(match.agreementId()).orElse(null);
        if (agreement == null) {
            return SuggestResponse.none();
        }
        String jobCategory = jobCategories.fromJobTitle(agreement, jobTitle).orElse(null);
        return new SuggestResponse(
                List.of(new Suggestion(match.agreementId(), match.confidence(), match.reason(), jobCategory)),
                source);
    }
}
