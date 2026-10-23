package com.tariffhelper.llm;

import com.tariffhelper.api.EstimateRequest;
import com.tariffhelper.domain.EstimateFacts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Used when no real LLM is configured. Makes no external calls; the numbers in the result are still real. */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "stub", matchIfMissing = true)
public class StubLlmClient implements LlmClient {

    @Override
    public LlmAssessment assess(EstimateRequest request, EstimateFacts facts) {
        return new LlmAssessment(
                "Placeholder explanation from the stub client; no AI was used. "
                        + "Counted experience: " + facts.seniority().countedYears() + " years"
                        + " (" + facts.seniority().countedYearsIfAllAccepted() + " years if the employer accepts "
                        + "every uncertain period). Minimum pay for your position group: "
                        + facts.estimatedMinimumSalary().min() + " to " + facts.estimatedMinimumSalary().max()
                        + " NOK per year at full time. Set LLM_PROVIDER=claude to get a written explanation.");
    }
}
