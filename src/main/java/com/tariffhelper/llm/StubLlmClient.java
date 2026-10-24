package com.tariffhelper.llm;

import com.tariffhelper.api.dto.request.EstimateRequest;
import com.tariffhelper.domain.EstimateFacts;

/** Used when {@code app.llm.provider=stub}. Makes no external calls; the numbers in the result are still real. */
public class StubLlmClient implements LlmClient {

    @Override
    public LlmAssessment assess(EstimateRequest request, EstimateFacts facts) {
        return new LlmAssessment(
                "Placeholder explanation from the stub client; no AI was used. "
                        + "Counted experience: " + facts.seniority().countedYears() + " years"
                        + " (" + facts.seniority().countedYearsIfAllAccepted() + " years if the employer accepts "
                        + "every uncertain period). Minimum pay for your position group: "
                        + facts.estimatedMinimumSalary().min() + " to " + facts.estimatedMinimumSalary().max()
                        + " NOK per year at full time. Set LLM_PROVIDER to llama, openai or anthropic to get a written explanation.");
    }
}
