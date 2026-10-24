package com.tariffhelper.llm;

import com.tariffhelper.api.dto.EstimateRequest;
import com.tariffhelper.domain.EstimateFacts;

/** Abstraction over the LLM provider, so Claude (or another model) can be swapped via config. */
public interface LlmClient {

    /** Writes the explanation for numbers the application has already calculated. */
    LlmAssessment assess(EstimateRequest request, EstimateFacts facts);
}
